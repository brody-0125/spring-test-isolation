#!/usr/bin/env bash
set -euo pipefail
Workers=2
Negative=false
while [[ $# -gt 0 ]]; do
  case "$1" in
    -Workers|--workers) Workers="$2"; shift 2 ;;
    -Negative|--negative) Negative=true; shift ;;
    *) echo "Usage: $0 [-Workers N] [-Negative]" >&2; exit 1 ;;
  esac
done

# shellcheck source=verify-common.sh
source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/verify-common.sh"
RepoRoot="$(get_repo_root)"
cd "$RepoRoot"
gradlew="$(resolve_gradlew "$RepoRoot")"
mkdir -p build/evidence

if [[ "$Negative" == true ]]; then
  log="build/evidence/workers-${Workers}-negative-True.log"
  set +e
  "$gradlew" :verification:cleanupFailureTest "-Pworkers=$Workers" --rerun-tasks --console=plain >"$log" 2>&1
  code=$?
  set -e
  if [[ "$Workers" -ne 1 ]]; then
    echo 'Cleanup poison verification requires -Workers 1' >&2
    exit 1
  fi
  if [[ "$code" -eq 0 ]] || ! grep -q 'INJECTED_CLEANUP_FAILURE' "$log" \
    || ! grep -q 'NEGATIVE_BODY CleanupATest' "$log" \
    || grep -q 'NEGATIVE_BODY CleanupBTest' "$log"; then
    echo "Cleanup poison contract failed; inspect $log" >&2
    exit 1
  fi
  if [[ "$(match_count 'PTK infrastructure-closed' "$log")" -ne 1 ]]; then
    echo 'Infrastructure did not close after cleanup failure' >&2
    exit 1
  fi
  echo 'PASS: injected cleanup failed and later class body did not execute.'
  exit 0
fi

log="build/evidence/workers-${Workers}-negative-False.log"
set +e
clear_cached_runtime_artifact "$RepoRoot"
"$gradlew" :descriptor:clean :descriptor:publishToMavenLocal :runtime:clean :runtime:publishToMavenLocal :runtime:test :verification:test "-Pworkers=$Workers" --rerun-tasks --console=plain >"$log" 2>&1
code=$?
set -e
if [[ "$code" -ne 0 ]]; then
  echo "Gradle failed; inspect $log" >&2
  exit 1
fi
if [[ "$(match_count 'PTK infrastructure-start ' "$log")" -ne 1 ]] \
  || [[ "$(match_count 'PTK infrastructure-closed' "$log")" -ne 1 ]]; then
  echo 'Invalid shared infrastructure lifecycle' >&2
  exit 1
fi

events_json="build/evidence/workers-${Workers}-events.json"
overlap="$(WORKERS="$Workers" LOG="$log" EVENTS_JSON="$events_json" perl -we '
  use strict; use warnings;
  my $workers = $ENV{WORKERS};
  my $log = $ENV{LOG};
  my $events_json = $ENV{EVENTS_JSON};
  open my $fh, "<", $log or die $!;
  local $/; my $t = <$fh>;
  my @events;
  while ($t =~ /EVIDENCE start time=(\d+) worker=(\d+) class=(\w+) context=(\d+)/g) {
    my ($time, $worker, $class, $ctx) = ($1, $2, $3, $4);
    push @events, { class => $class, worker => $worker, context => $ctx, start => $time };
  }
  my %ends;
  while ($t =~ /EVIDENCE end time=(\d+) worker=(\d+) class=(\w+)/g) { $ends{$3} = $1 }
  die "Expected six completed storage classes\n" unless @events == 6 && keys %ends == 6;
  for my $e (@events) {
    $e->{end} = $ends{$e->{class}} // die "Missing end for $e->{class}\n";
  }
  my $overlap = 0;
  for my $a (@events) {
    for my $b (@events) {
      next if $a->{class} eq $b->{class};
      if ($a->{start} < $b->{end} && $b->{start} < $a->{end}) {
        die "Concurrent test bodies inside a worker\n" if $a->{worker} eq $b->{worker};
        $overlap = 1;
      }
    }
  }
  die "No measured overlap across workers\n" if $workers > 1 && !$overlap;
  my %reuse;
  $reuse{"$_->{worker},$_->{context}"}++ for @events;
  die "No demonstrated Context reuse\n" unless grep { $_ > 1 } values %reuse;
  open my $out, ">", $events_json or die $!;
  print $out "[\n";
  my $i = 0;
  for my $e (@events) {
    print $out "," if $i++;
    printf $out qq(  {"Class":"%s","Worker":"%s","Context":"%s","Start":%s,"End":%s}\n),
      $e->{class}, $e->{worker}, $e->{context}, $e->{start}, $e->{end};
  }
  print $out "]\n";
  print $overlap;
')"

echo "PASS: storage scenarios, internal serial execution, Context reuse; cross-worker overlap=$overlap"

testng_log="build/evidence/workers-${Workers}-testng.log"
set +e
"$gradlew" :verification:testngSmoke "-Pworkers=$Workers" --rerun-tasks --console=plain >"$testng_log" 2>&1
testng_code=$?
set -e
if [[ "$testng_code" -ne 0 ]] || ! grep -q 'EVIDENCE start' "$testng_log" || ! grep -q 'PTK class-clean' "$testng_log"; then
  echo "TestNG storage smoke failed; inspect $testng_log" >&2
  exit 1
fi
echo 'PASS: TestNG native worker storage smoke'

kotest_log="build/evidence/workers-${Workers}-kotest.log"
set +e
"$gradlew" :verification:kotestSmoke "-Pworkers=$Workers" --rerun-tasks --console=plain >"$kotest_log" 2>&1
kotest_code=$?
set -e
if [[ "$kotest_code" -ne 0 ]] || ! grep -q 'PTK class-clean' "$kotest_log" || ! grep -q 'Auto-closing context after' "$kotest_log"; then
  echo "Kotest worker isolation failed; inspect $kotest_log" >&2
  exit 1
fi
WORKERS="$Workers" LOG="$kotest_log" perl -we '
  use strict; use warnings;
  open my $fh, "<", $ENV{LOG} or die $!;
  local $/; my $t = <$fh>;
  my @events;
  while ($t =~ /EVIDENCE start time=(\d+) worker=(\d+) class=(\w+) context=(\d+)/g) {
    push @events, { class => $3, worker => $2, context => $4, start => $1 };
  }
  my %ends;
  while ($t =~ /EVIDENCE end time=(\d+) worker=(\d+) class=(\w+)/g) { $ends{$3} = $1 }
  die "Expected four completed Kotest specs\n" unless @events == 4 && keys %ends == 4;
  for my $e (@events) { $e->{end} = $ends{$e->{class}} // die "Missing end for $e->{class}\n" }
  for my $a (@events) {
    for my $b (@events) {
      next if $a->{class} eq $b->{class};
      die "Concurrent Kotest spec bodies inside a worker\n"
        if $a->{start} < $b->{end} && $b->{start} < $a->{end} && $a->{worker} eq $b->{worker};
    }
  }
  my %reuse;
  $reuse{"$_->{worker},$_->{context}"}++ for @events;
  die "No demonstrated Kotest Context reuse\n" unless grep { $_ > 1 } values %reuse;
'
echo 'PASS: Kotest storage, Smart Context close, serial specs inside each worker'
