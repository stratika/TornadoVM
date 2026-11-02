#!/usr/bin/env bash
# Verifies a TornadoVM port: compiles a check written from CheckTemplate.java, runs it on two
# data sets, then times the original method and the kernel, and prints a summary that ends in
# PASS or FAIL.
#
# usage: verify.sh [-cp <classpath>] [-n <size>] [--debug] [-J <jvm option>]... Check<Name>.java
#   -cp      the project's compiled classes and jars, which hold the original method and the kernel
#   -n       the data size for the timing run (default 1048576)
#   --debug  run TornadoVM with --debug, to see why a kernel ran as Java
#   -J       an extra JVM option, for example -J -Xmx8g
set -euo pipefail

here="$(cd "$(dirname "$0")" && pwd)"
classpath=""
size=1048576
debug=""
jvm=()
check=""
while [ $# -gt 0 ]; do
    case "$1" in
        -cp|--classpath) classpath="$2"; shift 2 ;;
        -n) size="$2"; shift 2 ;;
        --debug) debug="--debug"; shift ;;
        -J) jvm+=("$2"); shift 2 ;;
        -h|--help) sed -n '2,11p' "$0"; exit 0 ;;
        *) check="$1"; shift ;;
    esac
done
if [ -z "$check" ] || [ ! -f "$check" ]; then
    echo "usage: verify.sh [-cp <classpath>] [-n <size>] [--debug] [-J <jvm option>]... Check<Name>.java" >&2
    exit 2
fi
if ! command -v tornado > /dev/null || [ -z "${TORNADOVM_HOME:-}" ]; then
    echo "FAIL setup: the tornado launcher and TORNADOVM_HOME are needed; install TornadoVM or source its setvars.sh" >&2
    exit 2
fi

main="$(basename "$check" .java)"
out="$(mktemp -d)"
trap 'rm -rf "$out"' EXIT
compile_cp="$TORNADOVM_HOME/share/java/tornado/*${classpath:+:$classpath}"
if ! javac -nowarn -d "$out" -cp "$compile_cp" "$here/PortCheck.java" "$check" 2> "$out/javac.log"; then
    cat "$out/javac.log"
    echo "FAIL compile: the check does not compile"
    exit 1
fi

run_cp="$out${classpath:+:$classpath}"
jvm_options="${jvm[*]:-}"
# run <size> <data set> <warm-ups> <runs>: the check's PORT lines, and the last output lines on a crash
run() {
    local log="$out/run-$2-$4.log"
    tornado $debug ${jvm_options:+--jvm="$jvm_options"} --classpath "$run_cp" "$main" "$@" > "$log" 2>&1 || true
    if grep -q '^PORT ' "$log"; then
        grep '^PORT ' "$log"
    else
        tail -20 "$log"
        echo "PORT kernel-failed the check printed no result; the output is above"
    fi
}

device="$(tornado --devices 2>/dev/null | sed 's/\x1b\[[0-9;]*m//g' | grep -A1 'DEFAULT' | tail -1 | sed 's/.*-- *//')"
echo "Device: ${device:-unknown}"

failed=""
for data in 1 2; do
    n=$((4096 + data - 1))
    lines="$(run "$n" "$data" 0 0)"
    echo "Data set $data ($n elements):"
    echo "$lines" | sed 's/^PORT /  /'
    if ! echo "$lines" | grep -q '^PORT equal'; then
        failed="$(echo "$lines" | grep -oE '^PORT (different|not-on-device|kernel-failed|original-failed)' | head -1 | cut -d' ' -f2)"
        failed="${failed:-different}"
        break
    fi
done
if [ -n "$failed" ]; then
    echo "FAIL $failed"
    exit 1
fi

lines="$(run "$size" 1 3 10)"
time_line="$(echo "$lines" | grep '^PORT time' || true)"
if [ -z "$time_line" ]; then
    echo "$lines" | sed 's/^PORT /  /'
    echo "FAIL $(echo "$lines" | grep -oE '^PORT (different|not-on-device|kernel-failed|original-failed)' | head -1 | cut -d' ' -f2)"
    exit 1
fi
read -r _ _ java kernel device_time copies <<< "$time_line"
awk -v n="$size" -v j="$java" -v k="$kernel" -v d="$device_time" -v c="$copies" 'BEGIN {
    printf "Timing (%d elements, median of 10 runs after 3 warm-ups):\n", n
    printf "  Java          %10.3f ms\n", j / 1e6
    printf "  Kernel        %10.3f ms  (on the device %.3f ms, copies %.3f ms)\n", k / 1e6, d / 1e6, c / 1e6
    s = j / k
    if (s >= 1.2) printf "  %.1fx faster than Java\n", s
    else if (s >= 1) printf "  %.2fx: not clearly faster than Java (under 1.2x)\n", s
    else printf "  %.1fx slower than Java\n", 1 / s
}'
echo "PASS"
