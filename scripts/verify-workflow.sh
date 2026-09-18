#!/usr/bin/env bash
set -euo pipefail
Workers=2
Runs=1
while [[ $# -gt 0 ]]; do
  case "$1" in
    -Workers|--workers) Workers="$2"; shift 2 ;;
    -Runs|--runs) Runs="$2"; shift 2 ;;
    *) echo "Usage: $0 [-Workers 1|2|4] [-Runs N]" >&2; exit 1 ;;
  esac
done
case "$Workers" in 1|2|4) ;; *)
  echo 'Workers must be 1, 2, or 4' >&2
  exit 1
esac

# shellcheck source=verify-common.sh
source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/verify-common.sh"
RepoRoot="$(get_repo_root)"
cd "$RepoRoot"
gradlew="$(resolve_gradlew "$RepoRoot")"
mkdir -p build/evidence

for run in $(seq 1 "$Runs"); do
  log="build/evidence/workflow-${Workers}-run-${run}.log"
  set +e
  "$gradlew" :verification:workflowTest "-Pworkers=$Workers" --rerun-tasks --console=plain >"$log" 2>&1
  workflowCode=$?
  set -e
  if [[ "$workflowCode" -ne 0 ]]; then
    echo "Workflow integration failed; inspect $log" >&2
    exit 1
  fi
  if grep -qE 'Invocation of (close|destroy) method failed|Worker storage cleanup failed' "$log"; then
    echo 'Lifecycle cleanup warning was swallowed by the framework' >&2
    exit 1
  fi
  for pattern in 'PTK infrastructure-start ' 'PTK infrastructure-closed'; do
    if [[ "$(match_count "$pattern" "$log")" -ne 1 ]]; then
      echo "Infrastructure lifecycle mismatch: $pattern" >&2
      exit 1
    fi
  done
  for pattern in 'WORKFLOW verified ' 'WORKFLOW drained ' 'WORKFLOW audited ' 'PTK class-clean worker=[0-9]+ class=workflow\.'; do
    if [[ "$(match_count "$pattern" "$log")" -ne 4 ]]; then
      echo "Expected four completed boundaries: $pattern" >&2
      exit 1
    fi
  done
  if [[ "$(match_count 'WORKFLOW destroyed ' "$log")" -ne "$Workers" ]]; then
    echo 'Expected one successful context destruction per worker' >&2
    exit 1
  fi
  peers_pattern="WORKFLOW verified worker=[0-9]+ peers=${Workers} cleaner="
  if [[ "$(match_count "$peers_pattern" "$log")" -ne 4 ]]; then
    echo 'Barrier did not prove the requested cross-worker concurrency' >&2
    exit 1
  fi

  WORKERS="$Workers" LOG="$log" RUN="$run" perl -we '
    use strict; use warnings;
    my $workers = $ENV{WORKERS};
    my $log = $ENV{LOG};
    my $run = $ENV{RUN};
    open my $fh, "<", $log or die $!;
    local $/; my $t = <$fh>;
    my @starts = map { [@$_] } m/WORKFLOW start time=(\d+) worker=(\d+) class=(\w+) context=(\d+)/g;
    my %ends;
    while ($t =~ /WORKFLOW end time=(\d+) worker=(\d+) class=(\w+)/g) { $ends{$3} = [$1, $2] }
    die "Expected four workflow classes\n" unless @starts == 4 && keys %ends == 4;
    my @events;
    for my $s (@starts) {
      my ($time, $worker, $class, $ctx) = @$s;
      my $end = $ends{$class} // die "Missing end for $class\n";
      die "Missing or duplicate class completion\n" unless @$end == 2;
      push @events, { class => $class, worker => $worker, context => $ctx, start => $time, end => $end->[0] };
    }
    my %unique_workers = map { $_->{worker} => 1 } @events;
    die "Unexpected worker count\n" unless scalar keys %unique_workers == $workers;
    for my $a (@events) {
      for my $b (@events) {
        next if $a->{class} eq $b->{class};
        next unless $a->{worker} eq $b->{worker};
        die "Concurrent classes inside a worker\n"
          if $a->{start} < $b->{end} && $b->{start} < $a->{end};
      }
    }
    if ($workers < 4) {
      my %reuse;
      $reuse{"$_->{worker},$_->{context}"}++ for @events;
      die "Context reuse was not observed\n" unless grep { $_ > 1 } values %reuse;
    }
    my $events_json = "build/evidence/workflow-${workers}-run-${run}-events.json";
    open my $out, ">", $events_json or die $!;
    print $out "[\n";
    my $i = 0;
    for my $e (@events) {
      print $out "," if $i++;
      printf $out qq(  {"Class":"%s","Worker":"%s","Context":"%s","Start":%s,"End":%s}\n),
        $e->{class}, $e->{worker}, $e->{context}, $e->{start}, $e->{end};
    }
    print $out "]\n";
  '

  count=0
  shopt -s nullglob
  for file in verification/build/test-results/workflowTest/TEST-*.xml; do
    if grep -qE 'failures="[1-9]' "$file" || grep -qE 'errors="[1-9]' "$file" || grep -qE 'skipped="[1-9]' "$file"; then
      echo "Failed or skipped test in $file" >&2
      exit 1
    fi
    tests=$(grep -oE 'tests="[0-9]+"' "$file" | head -1 | sed 's/tests="//;s/"//')
    count=$((count + tests))
  done
  shopt -u nullglob
  if [[ "$count" -ne 4 ]]; then
    echo "Expected four successful tests, got $count" >&2
    exit 1
  fi

  jdbc_id=$(grep -Eo 'PTK infrastructure-start jdbc=[^ ]+' "$log" | head -1 | sed 's/.*jdbc=//')
  cache_id=$(grep -Eo 'cache=[^ ]+' "$log" | head -1 | sed 's/cache=//')
  remaining=$(docker ps -aq --no-trunc)
  if [[ $? -ne 0 ]]; then
    echo 'Cannot verify container removal' >&2
    exit 1
  fi
  if echo "$remaining" | grep -qxF "$jdbc_id" || echo "$remaining" | grep -qxF "$cache_id"; then
    echo 'Build containers leaked' >&2
    exit 1
  fi
  echo "PASS: workflow workers=$Workers run=$run; transactions, retry, deduplication, local reset, late work, lifecycle."
done
