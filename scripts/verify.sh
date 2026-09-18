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
"$gradlew" :runtime:test :verification:test "-Pworkers=$Workers" --rerun-tasks --console=plain >"$log" 2>&1
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
  my @starts = map { [@$_] } m/EVIDENCE start time=(\d+) worker=(\d+) class=(\w+) context=(\d+)/g;
  my %ends;
  while ($t =~ /EVIDENCE end time=(\d+) worker=(\d+) class=(\w+)/g) { $ends{$3} = $1 }
  die "Expected six completed storage classes\n" unless @starts == 6 && keys %ends == 6;
  my @events;
  for my $s (@starts) {
    my ($time, $worker, $class, $ctx) = @$s;
    my $end = $ends{$class} // die "Missing end for $class\n";
    push @events, { class => $class, worker => $worker, context => $ctx, start => $time, end => $end };
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
