#!/usr/bin/env bash
# ForgeShell Automatic VPS Dependency Installer (--break-system-packages) & Runner

forgeshell_ensure_pip() {
  if ! command -v python3 >/dev/null 2>&1; then
    export DEBIAN_FRONTEND=noninteractive
    if command -v apt-get >/dev/null 2>&1; then
      (apt-get update -y && apt-get install -y python3 python3-pip python3-venv) || (sudo -n apt-get update -y && sudo -n apt-get install -y python3 python3-pip python3-venv)
    elif command -v apk >/dev/null 2>&1; then
      apk add --no-cache python3 py3-pip
    elif command -v dnf >/dev/null 2>&1; then
      dnf install -y python3 python3-pip
    fi
  elif ! python3 -m pip --version >/dev/null 2>&1; then
    export DEBIAN_FRONTEND=noninteractive
    if command -v apt-get >/dev/null 2>&1; then
      (apt-get update -y && apt-get install -y python3-pip) || (sudo -n apt-get update -y && sudo -n apt-get install -y python3-pip)
    elif command -v apk >/dev/null 2>&1; then
      apk add --no-cache py3-pip
    elif command -v dnf >/dev/null 2>&1; then
      dnf install -y python3-pip
    fi
  fi
}

forgeshell_pip_install() {
  python3 -m pip install --break-system-packages "$@" || python3 -m pip install "$@"
}

forgeshell_auto_py_deps() {
  local script_path="$1"
  local script_dir
  script_dir="$(cd "$(dirname "$script_path")" 2>/dev/null && pwd || pwd)"

  forgeshell_ensure_pip

  local req_file=""
  if [ -f "$script_dir/requirements.txt" ]; then
    req_file="$script_dir/requirements.txt"
  elif [ -f "./requirements.txt" ]; then
    req_file="./requirements.txt"
  fi

  if [ -n "$req_file" ]; then
    local req_hash=""
    if command -v md5sum >/dev/null 2>&1; then
      req_hash="$(md5sum "$req_file" | awk '{print $1}')"
    fi
    local stamp_file="$script_dir/.forgeshell_req_done"
    if [ -z "$req_hash" ] || [ ! -f "$stamp_file" ] || [ "$(cat "$stamp_file" 2>/dev/null)" != "$req_hash" ]; then
      printf '\033[38;5;208m[ForgeShell] Installing requirements.txt (--break-system-packages)...\033[0m\n'
      if forgeshell_pip_install -r "$req_file"; then
        [ -n "$req_hash" ] && echo "$req_hash" > "$stamp_file"
      fi
    fi
  fi

  local missing_pkgs
  missing_pkgs="$(python3 - "$script_dir" "$script_path" << 'PYEOF'
import ast, importlib.util, os, sys

proj_dir = sys.argv[1]
target_file = sys.argv[2]

ALIAS_MAP = {
    "telebot": "pyTelegramBotAPI",
    "telegram": "python-telegram-bot",
    "dotenv": "python-dotenv",
    "PIL": "Pillow",
    "cv2": "opencv-python-headless",
    "bs4": "beautifulsoup4",
    "yaml": "PyYAML",
    "Crypto": "pycryptodome",
    "Cryptodome": "pycryptodomex",
    "OpenSSL": "pyOpenSSL",
    "sklearn": "scikit-learn",
    "fitz": "PyMuPDF",
    "discord": "discord.py",
    "dateutil": "python-dateutil",
    "dns": "dnspython",
    "websocket": "websocket-client",
    "serial": "pyserial",
    "jwt": "PyJWT",
    "socks": "PySocks",
    "magic": "python-magic",
}

stdlib = set(getattr(sys, "stdlib_module_names", ())) | {
    "os", "sys", "re", "json", "time", "math", "random", "logging", "asyncio",
    "threading", "subprocess", "pathlib", "typing", "collections", "itertools",
    "functools", "datetime", "hashlib", "hmac", "base64", "urllib", "http",
    "socket", "ssl", "sqlite3", "csv", "io", "shutil", "tempfile", "glob",
    "argparse", "configparser", "traceback", "uuid", "signal", "ctypes",
    "multiprocessing", "concurrent", "queue", "struct", "copy", "enum",
    "dataclasses", "abc", "contextlib", "inspect", "ast", "importlib",
    "zipfile", "tarfile", "gzip", "zlib", "platform", "getpass", "pprint",
    "__future__"
}

local_mods = set()
try:
    for entry in os.listdir(proj_dir):
        if entry.endswith(".py"):
            local_mods.add(entry[:-3])
        elif os.path.isdir(os.path.join(proj_dir, entry)):
            local_mods.add(entry)
except Exception:
    pass

files_to_scan = []
if os.path.isfile(target_file):
    files_to_scan.append(target_file)
try:
    for root, dirs, files in os.walk(proj_dir):
        dirs[:] = [d for d in dirs if not d.startswith(".") and d not in ("venv", ".venv", "node_modules", "__pycache__")]
        for f in files:
            if f.endswith(".py"):
                full = os.path.join(root, f)
                if full not in files_to_scan:
                    files_to_scan.append(full)
        if len(files_to_scan) > 40:
            break
except Exception:
    pass

imports = set()
for fp in files_to_scan[:40]:
    try:
        with open(fp, "r", encoding="utf-8", errors="ignore") as fh:
            tree = ast.parse(fh.read(), filename=fp)
        for node in ast.walk(tree):
            if isinstance(node, ast.Import):
                for alias in node.names:
                    top = alias.name.split(".")[0]
                    if top:
                        imports.add(top)
            elif isinstance(node, ast.ImportFrom):
                if node.level == 0 and node.module:
                    top = node.module.split(".")[0]
                    if top:
                        imports.add(top)
    except Exception:
        pass

sys.path.insert(0, proj_dir)
missing = []
for mod in sorted(imports):
    if mod in stdlib or mod in local_mods or mod.startswith("_"):
        continue
    try:
        spec = importlib.util.find_spec(mod)
    except Exception:
        spec = None
    if spec is None:
        pkg = ALIAS_MAP.get(mod, mod)
        if pkg not in missing:
            missing.append(pkg)

print(" ".join(missing))
PYEOF
)"

  if [ -n "$missing_pkgs" ]; then
    printf '\033[38;5;208m[ForgeShell] Auto-installing missing packages (--break-system-packages): %s\033[0m\n' "$missing_pkgs"
    forgeshell_pip_install $missing_pkgs
  fi
}

forgeshell_auto_node_deps() {
  local script_path="$1"
  local script_dir
  script_dir="$(cd "$(dirname "$script_path")" 2>/dev/null && pwd || pwd)"
  if ! command -v node >/dev/null 2>&1 || ! command -v npm >/dev/null 2>&1; then
    export DEBIAN_FRONTEND=noninteractive
    if command -v apt-get >/dev/null 2>&1; then
      printf '\033[38;5;208m[ForgeShell] Installing Node.js & npm...\033[0m\n'
      (apt-get update -y && apt-get install -y nodejs npm) || (sudo -n apt-get update -y && sudo -n apt-get install -y nodejs npm)
    elif command -v apk >/dev/null 2>&1; then
      apk add --no-cache nodejs npm
    fi
  fi
  if [ -f "$script_dir/package.json" ] && [ ! -d "$script_dir/node_modules" ]; then
    printf '\033[38;5;208m[ForgeShell] Running npm install...\033[0m\n'
    (cd "$script_dir" && npm install)
  fi
}

python3() {
  if [ -z "$FORGESHELL_IN_PY" ] && [ $# -ge 1 ] && [ -f "$1" ]; then
    case "$1" in
      *.py) FORGESHELL_IN_PY=1 forgeshell_auto_py_deps "$1" ;;
    esac
  fi
  FORGESHELL_IN_PY=1 command python3 "$@"
}

python() {
  if [ -z "$FORGESHELL_IN_PY" ] && [ $# -ge 1 ] && [ -f "$1" ]; then
    case "$1" in
      *.py) FORGESHELL_IN_PY=1 forgeshell_auto_py_deps "$1" ;;
    esac
  fi
  if command -v python >/dev/null 2>&1; then
    FORGESHELL_IN_PY=1 command python "$@"
  else
    FORGESHELL_IN_PY=1 command python3 "$@"
  fi
}

node() {
  if [ -z "$FORGESHELL_IN_NODE" ] && [ $# -ge 1 ] && [ -f "$1" ]; then
    forgeshell_auto_node_deps "$1"
  fi
  FORGESHELL_IN_NODE=1 command node "$@"
}

forgerun() {
  local target="$1"
  if [ -z "$target" ] || [ ! -f "$target" ]; then
    echo "[ForgeShell] File not found: $target"
    return 1
  fi
  local dir
  dir="$(cd "$(dirname "$target")" && pwd)"
  local base
  base="$(basename "$target")"
  cd "$dir" || return 1
  printf '\033[38;5;208m[ForgeShell] Preparing & hosting %s in %s...\033[0m\n' "$base" "$dir"
  case "$base" in
    *.py)
      python3 "$base"
      ;;
    *.js|*.mjs|*.cjs)
      forgeshell_auto_node_deps "$dir/$base"
      command node "$base"
      ;;
    *.sh)
      chmod +x "$base"
      if [ -f "$dir/requirements.txt" ]; then
        forgeshell_ensure_pip
        forgeshell_pip_install -r "$dir/requirements.txt"
      fi
      if [ -f "$dir/package.json" ] && [ ! -d "$dir/node_modules" ]; then
        forgeshell_auto_node_deps "$dir/$base"
      fi
      bash "./$base"
      ;;
    *)
      chmod +x "$base" 2>/dev/null
      "./$base"
      ;;
  esac
}
