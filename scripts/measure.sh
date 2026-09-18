#!/usr/bin/env bash
set -euo pipefail
Workers=2
while [[ $# -gt 0 ]]; do
  case "$1" in
    -Workers|--workers) Workers="$2"; shift 2 ;;
    *) echo "Usage: $0 [-Workers N]" >&2; exit 1 ;;
  esac
done

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=verify-common.sh
source "$script_dir/verify-common.sh"
RepoRoot="$(get_repo_root)"
cd "$RepoRoot"
mkdir -p build/evidence

logPath="$RepoRoot/build/evidence/workers-${Workers}-negative-False.log"
samplePath="$RepoRoot/build/evidence/workers-${Workers}-memory.json"
: >"$logPath"

observe_memory() {
  local log="$1" out="$2"
  python3 - "$log" "$out" <<'PY'
import json, re, sys, time
from datetime import datetime, timezone

log_path, sample_path = sys.argv[1], sys.argv[2]
samples = []
deadline = time.time() + 300
pid_re = re.compile(r"Starting \w+ using Java [^\n]+ with PID (\d+)")

def rss_for_pids(pids):
    total = 0
    for pid in pids:
        try:
            with open(f"/proc/{pid}/status") as f:
                for line in f:
                    if line.startswith("VmRSS:"):
                        total += int(line.split()[1]) * 1024
                        break
        except OSError:
            pass
    return total

while time.time() < deadline:
    try:
        text = open(log_path, encoding="utf-8", errors="replace").read()
    except OSError:
        text = ""
    if text:
        pids = sorted({int(m.group(1)) for m in pid_re.finditer(text)})
        if pids:
            rss = rss_for_pids(pids)
            samples.append({
                "Time": int(datetime.now(timezone.utc).timestamp() * 1000),
                "Processes": len(pids),
                "WorkingSetBytes": rss,
                "PrivateBytes": rss,
            })
        if re.search(r"BUILD (SUCCESSFUL|FAILED)", text):
            break
    time.sleep(0.25)

with open(sample_path, "w", encoding="utf-8") as f:
    json.dump(samples, f)
PY
}

observer_pid=""
if [[ "$(uname -s)" == "Darwin" ]]; then
  observe_memory_mac() {
    local log="$1" out="$2"
    python3 - "$log" "$out" <<'PY'
import json, re, subprocess, sys, time
from datetime import datetime, timezone

log_path, sample_path = sys.argv[1], sys.argv[2]
samples = []
deadline = time.time() + 300
pid_re = re.compile(r"Starting \w+ using Java [^\n]+ with PID (\d+)")

def rss_for_pids(pids):
    total = 0
    for pid in pids:
        try:
            out = subprocess.check_output(["ps", "-o", "rss=", "-p", str(pid)], text=True).strip()
            total += int(out) * 1024
        except (subprocess.CalledProcessError, ValueError):
            pass
    return total

while time.time() < deadline:
    try:
        text = open(log_path, encoding="utf-8", errors="replace").read()
    except OSError:
        text = ""
    if text:
        pids = sorted({int(m.group(1)) for m in pid_re.finditer(text)})
        if pids:
            rss = rss_for_pids(pids)
            samples.append({
                "Time": int(datetime.now(timezone.utc).timestamp() * 1000),
                "Processes": len(pids),
                "WorkingSetBytes": rss,
                "PrivateBytes": rss,
            })
        if re.search(r"BUILD (SUCCESSFUL|FAILED)", text):
            break
    time.sleep(0.25)

with open(sample_path, "w", encoding="utf-8") as f:
    json.dump(samples, f)
PY
  }
  observe_memory_mac "$logPath" "$samplePath" &
  observer_pid=$!
else
  observe_memory "$logPath" "$samplePath" &
  observer_pid=$!
fi

start=$(date +%s)
"$script_dir/verify.sh" -Workers "$Workers"
end=$(date +%s)
wait "$observer_pid" 2>/dev/null || true

echo "Wall clock including Gradle and container startup: $((end - start)) seconds"
