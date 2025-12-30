#!/bin/bash
# ==========================================================
# WisePen 本地开发环境初始化脚本
# 用途：解决 macOS 重启后 /tmp 被清空导致 LibreOffice wrapper 丢失的问题
# 使用：sh scripts/dev-setup.sh
# ==========================================================

REAL_LO="/Applications/LibreOffice.app"
WRAPPER_LO="/tmp/wisepen/LibreOffice.app"

if [ ! -d "$REAL_LO" ]; then
  echo "❌ LibreOffice 未安装在 $REAL_LO，请先安装"
  exit 1
fi

if [ -f "$WRAPPER_LO/Contents/MacOS/soffice" ]; then
  echo "✅ LibreOffice wrapper 已存在，跳过"
else
  echo "🔧 创建 LibreOffice wrapper..."

  rm -rf "$WRAPPER_LO"
  mkdir -p "$WRAPPER_LO/Contents/MacOS"

  # 符号链接 Contents 下的目录和文件
  cd "$WRAPPER_LO/Contents"
  for item in Frameworks Resources Library PlugIns Info.plist PkgInfo _CodeSignature; do
    ln -sf "$REAL_LO/Contents/$item" . 2>/dev/null
  done

  # 符号链接 MacOS 下除 soffice 外的所有文件
  cd "$WRAPPER_LO/Contents/MacOS"
  for f in "$REAL_LO/Contents/MacOS/"*; do
    b=$(basename "$f")
    if [ "$b" != "soffice" ]; then
      ln -sf "$f" .
    fi
  done

  # 创建 soffice wrapper（即使是 LibreOffice 26.x 也能完美运行）
  # 1. 拦截 --help/--version 防止卡死
  # 2. 强制 --headless 为第一个参数
  # 3. 激进清理 JODConverter 临时 profile 目录（防止 corrupted profile 导致挂起）
  # 4. 详细调试日志到 /tmp/wisepen/soffice_debug.log
  cat > "$WRAPPER_LO/Contents/MacOS/soffice" << 'EOF'
#!/bin/bash
REAL_SOFFICE="/Applications/LibreOffice.app/Contents/MacOS/soffice"
LOG="/tmp/wisepen/soffice_debug.log"

echo "========================================" >> "$LOG"
echo "[$(date '+%Y-%m-%d %H:%M:%S')] invoked: $@" >> "$LOG"
echo "  PWD: $(pwd)" >> "$LOG"
echo "  USER: $(whoami)" >> "$LOG"

# Start new args with --headless forced as first argument
NEW_ARGS=("--headless")

for arg in "$@"; do
  # 1. Intercept Help/Version (single and double dash)
  if [[ "$arg" == "--help" || "$arg" == "-help" || "$arg" == "-h" || "$arg" == "--version" || "$arg" == "-version" ]]; then
    echo "  ACTION: Intercepted $arg, returning hardcoded version" >> "$LOG"
    echo "LibreOffice 26.2.0.3 afbbd0df0edb6d40b450b0337ac646b0913a760c"
    exit 0
  fi

  # 2. Aggressive Profile Cleanup
  if [[ "$arg" == -env:UserInstallation=file://* ]]; then
    PROFILE_URL="${arg#*-env:UserInstallation=}"
    PROFILE_PATH="${PROFILE_URL#file:}"
    echo "  CHECK: Profile path detected: $PROFILE_PATH" >> "$LOG"
    
    # Only clean if it looks like a JODConverter temp profile
    if [[ "$PROFILE_PATH" == *".jodconverter_"* ]]; then
      if [ -d "$PROFILE_PATH" ]; then
        echo "  ACTION: Aggressively removing corrupt/stale temp profile: $PROFILE_PATH" >> "$LOG"
        rm -rf "$PROFILE_PATH"
      else
        echo "  INFO: Profile path does not exist yet (clean start)" >> "$LOG"
      fi
    else
      # For non-temp profiles, only remove lock
      if [ -d "$PROFILE_PATH" ]; then
         echo "  ACTION: Cleaning lock file in permanent profile: $PROFILE_PATH" >> "$LOG"
         rm -f "$PROFILE_PATH/.lock"
      fi
    fi
  fi

  # 3. Sanitize and Filter Arguments
  CLEAN_ARG="$arg"
  
  # Skip headless if found (since we forced it at start)
  if [[ "$arg" == "-headless" || "$arg" == "--headless" ]]; then
    continue
  fi

  # Sanitize single-dash args to double-dash
  case "$arg" in
    -invisible|-nocrashreport|-nodefault|-nofirststartwizard|-nolockcheck|-nologo|-norestore|-accept=*)
      CLEAN_ARG="-${arg}"
      echo "  ACTION: Sanitized $arg -> $CLEAN_ARG" >> "$LOG"
      ;;
  esac
  
  NEW_ARGS+=("$CLEAN_ARG")
done

  if [ $? -eq 0 ]; then
    echo "  INFO: Profile cleanup successful" >> "$LOG"
  else
    echo "  ERROR: Profile cleanup FAILED" >> "$LOG"
  fi

# Clean environment
unset DYLD_LIBRARY_PATH
unset DYLD_INSERT_LIBRARIES
unset LD_LIBRARY_PATH
unset PYTHONPATH

# Full Debug Mode
DEBUG_LOG="/tmp/wisepen/soffice_full_debug.log"
exec >> "$DEBUG_LOG" 2>&1

echo "=============================================="
echo "WRAPPER PID: $$"
echo "DATE: $(date)"
echo "USER: $(whoami)"
echo "ID: $(id)"
echo "PWD: $(pwd)"
echo "ULIMIT:"
ulimit -a
echo "ENV:"
env
echo "----------------------------------------------"
echo "LAUNCHING (Clean Env + exec): $REAL_SOFFICE ${NEW_ARGS[*]}"

# Run with pristine environment (mimic successful manual run) using exec
# exec replaces the shell process so PID matches and signals propagate correctly
# NOTE: Use >> (append) to preserve previous debug info!
# exec replaces the shell process so PID matches and signals propagate correctly
# NOTE: Use >> (append) to preserve previous debug info!
# exec replaces the shell process so PID matches and signals propagate correctly
# NOTE: Use >> (append) to preserve previous debug info!
# STRATEGY: Do NOT use env -i (causes fstat hang). Use unset to remove bad vars.
# Ensure critical vars are set from current shell (which inherits from Java but we sanitize)
export LANG="${LANG:-en_US.UTF-8}"
export LC_ALL="${LC_ALL:-en_US.UTF-8}"

# CRITICAL FIX: macOS default open files limit is 2 billion (unlimited).
# Soffice initialization iterates over ALL FDs, causing massive hang/loop.
# We must cap it to a reasonable number.
ulimit -n 4096

echo "HARD LIMIT FIX APPLIED: ulimit -n 4096" >> /tmp/wisepen/soffice_full_debug.log

exec "$REAL_SOFFICE" "${NEW_ARGS[@]}" >> /tmp/wisepen/soffice_full_debug.log 2>&1 < /dev/null
EOF
  chmod +x "$WRAPPER_LO/Contents/MacOS/soffice"
  echo "✅ LibreOffice wrapper 创建完成"
fi

# 清理残留的 JODConverter profile（防止锁文件导致卡死）
PROFILE_DIR="/Users/$(whoami)/wisepen_office_profile"
if ls "$PROFILE_DIR"/.jodconverter_* 1>/dev/null 2>&1; then
  rm -rf "$PROFILE_DIR"/.jodconverter_*
  echo "🧹 已清理残留的 JODConverter profile"
fi

echo "🚀 开发环境初始化完成，可以启动 wisepen-file-service"
