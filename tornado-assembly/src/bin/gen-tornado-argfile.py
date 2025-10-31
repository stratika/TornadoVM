#!/usr/bin/env python3
#
# Copyright (c) 2025, APT Group, Department of Computer Science,
# The University of Manchester.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
# http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

"""
gen-tornado-argfile.py
Generate tornado-argfile from tornado --printJavaFlags and export lists
"""

import os
import sys
import subprocess
import re
from pathlib import Path


def strip_comments(file_path):
    """Strip comment and empty lines from export lists."""
    try:
        with open(file_path, 'r') as f:
            lines = f.readlines()

        # Filter out lines that are comments (starting with #) or empty
        filtered_lines = []
        for line in lines:
            stripped = line.strip()
            if stripped and not stripped.startswith('#'):
                filtered_lines.append(line.rstrip())

        return filtered_lines
    except FileNotFoundError:
        print(f"[WARNING] File not found: {file_path}", file=sys.stderr)
        return []


def main():
    if len(sys.argv) < 2:
        print("Usage: gen-tornado-argfile.py <backends>", file=sys.stderr)
        print("Example: gen-tornado-argfile.py opencl,ptx,spirv", file=sys.stderr)
        sys.exit(1)

    backends = sys.argv[1]

    # Get TORNADO_SDK from environment
    tornado_sdk = os.environ.get('TORNADO_SDK')
    if not tornado_sdk:
        print("[ERROR] TORNADO_SDK environment variable is not set", file=sys.stderr)
        sys.exit(1)

    # Project root: go two levels up from $TORNADO_SDK (…/TornadoVM/bin/sdk -> …/TornadoVM)
    project_root = Path(tornado_sdk).parent.parent.resolve()

    tornado_bin = "tornado"
    out_file = project_root / "tornado-argfile"
    export_common = Path(tornado_sdk) / "etc" / "exportLists" / "common-exports"
    export_opencl = Path(tornado_sdk) / "etc" / "exportLists" / "opencl-exports"
    export_spirv = Path(tornado_sdk) / "etc" / "exportLists" / "spirv-exports"
    export_ptx = Path(tornado_sdk) / "etc" / "exportLists" / "ptx-exports"

    print("[INFO] Cleaning old file")
    if out_file.exists():
        out_file.unlink()

    print("[INFO] Generating TornadoVM argfile")

    # Run tornado --printJavaFlags
    try:
        result = subprocess.run(
            [tornado_bin, "--printJavaFlags"],
            capture_output=True,
            text=True,
            check=True
        )
        flags_output = result.stdout.strip()
    except subprocess.CalledProcessError as e:
        print(f"[ERROR] Failed to run 'tornado --printJavaFlags': {e}", file=sys.stderr)
        sys.exit(1)
    except FileNotFoundError:
        print(f"[ERROR] 'tornado' command not found. Make sure it's in your PATH", file=sys.stderr)
        sys.exit(1)

    # Extract flags after "java "
    if "java " in flags_output:
        flags_str = flags_output.split("java ", 1)[1]
    else:
        flags_str = flags_output

    # Split flags into array
    java_flags = flags_str.split()

    # Open output file and write sections
    with open(out_file, 'w') as f:
        # Section 1: JVM mode and memory settings
        f.write("# === JVM mode and memory settings ===\n")
        for flag in java_flags:
            if flag.startswith('-XX') or flag == '-server' or flag == '--enable-preview':
                f.write(f"{flag}\n")

        # Section 2: Native library path
        f.write("\n# === Native library path ===\n")
        for flag in java_flags:
            if flag.startswith('-Djava.library.path'):
                f.write(f"{flag}\n")

        # Section 3: Tornado runtime classes
        f.write("\n# === Tornado runtime classes ===\n")
        for flag in java_flags:
            if flag.startswith('-Dtornado'):
                f.write(f"{flag}\n")

        # Section 4: Module system
        f.write("\n# === Module system ===\n")
        i = 0
        while i < len(java_flags):
            flag = java_flags[i]
            if flag in ['--module-path', '--upgrade-module-path', '--add-modules']:
                if i + 1 < len(java_flags):
                    f.write(f"{flag} {java_flags[i + 1]}\n")
                    i += 1  # Skip the next element as it's the value
            i += 1

        # Section 5: Export lists
        f.write("\n# === Export lists ===\n\n")

        # Common exports
        f.write(f"# ===: {export_common.name} ===\n")
        for line in strip_comments(export_common):
            f.write(f"{line}\n")
        f.write("\n")

        # Backend-specific exports
        backend_list = [b.strip() for b in backends.split(',')]

        for backend in backend_list:
            if backend == "opencl":
                f.write(f"# === {export_opencl.name} ===\n")
                for line in strip_comments(export_opencl):
                    f.write(f"{line}\n")
                f.write("\n")
            elif backend == "ptx":
                f.write(f"# === {export_ptx.name} ===\n")
                for line in strip_comments(export_ptx):
                    f.write(f"{line}\n")
                f.write("\n")
            elif backend == "spirv":
                f.write(f"# === {export_spirv.name} ===\n")
                for line in strip_comments(export_spirv):
                    f.write(f"{line}\n")
                f.write("\n")

    print("[INFO] Done. Generated fresh argfile")
    print(f"[INFO] File path: {out_file}")


if __name__ == "__main__":
    main()
