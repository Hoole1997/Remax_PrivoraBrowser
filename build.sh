#!/bin/bash

# Android 项目打包脚本
# 支持分渠道打包，自动下载 JDK 和 Android SDK，无需 Android Studio 环境
# 支持模块化执行：环境下载、环境设置、打包命令

# 注意：不使用 set -e，因为某些检查命令可能返回非零退出码
# set -e  # 遇到错误立即退出

# 颜色输出
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
PURPLE='\033[0;35m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

# 项目根目录
PROJECT_ROOT=$(cd "$(dirname "$0")" && pwd)
cd "$PROJECT_ROOT"

# 本地环境目录
LOCAL_ENV_DIR="$PROJECT_ROOT/.build_env"
LOCAL_JDK_DIR="$LOCAL_ENV_DIR/jdk"
LOCAL_SDK_DIR="$LOCAL_ENV_DIR/android-sdk"

# 输出目录
OUTPUT_DIR="$PROJECT_ROOT/outputs"

# 渠道配置 (可自定义)
# 格式: "渠道名称:签名目录路径" (路径相对于 app/src/)
# 默认渠道
DEFAULT_CHANNELS="playstore internal"

# 从配置文件加载渠道 (如果存在)
CHANNELS_CONFIG_FILE="$PROJECT_ROOT/.build_channels"
if [ -f "$CHANNELS_CONFIG_FILE" ]; then
    # 配置文件格式: 每行一个渠道名
    CONFIGURED_CHANNELS=$(cat "$CHANNELS_CONFIG_FILE" | grep -v "^#" | grep -v "^$" | tr '\n' ' ')
    if [ -n "$CONFIGURED_CHANNELS" ]; then
        DEFAULT_CHANNELS="$CONFIGURED_CHANNELS"
    fi
fi

# 系统检测
OS_TYPE=""
ARCH_TYPE=""

detect_system() {
    case "$OSTYPE" in
        linux-gnu*)
            OS_TYPE="linux"
            ;;
        darwin*)
            OS_TYPE="macos"
            ;;
        msys*|mingw*|cygwin*|win32)
            OS_TYPE="windows"
            ;;
        *)
            OS_TYPE="unknown"
            ;;
    esac

    case $(uname -m) in
        x86_64|amd64)
            ARCH_TYPE="x64"
            ;;
        arm64|aarch64)
            ARCH_TYPE="arm64"
            ;;
        *)
            ARCH_TYPE="x64"  # 默认
            ;;
    esac

    log_info "检测到系统: $OS_TYPE-$ARCH_TYPE"
}

# 日志函数
log_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

log_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

log_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

log_download() {
    echo -e "${PURPLE}[DOWNLOAD]${NC} $1"
}

log_setup() {
    echo -e "${CYAN}[SETUP]${NC} $1"
}

log_git() {
    echo -e "${PURPLE}[GIT]${NC} $1"
}

# 下载进度显示
download_with_progress() {
    local url=$1
    local output=$2
    local description=$3

    log_download "正在下载 $description..."
    log_download "URL: $url"

    if command -v curl &> /dev/null; then
        curl -L --progress-bar -o "$output" "$url"
    elif command -v wget &> /dev/null; then
        wget --progress=bar:force -O "$output" "$url"
    else
        log_error "未找到 curl 或 wget，无法下载文件"
        exit 1
    fi
}

# 从项目配置中读取版本信息
get_project_versions() {
    # 读取 Gradle 版本
    if [ -f "$PROJECT_ROOT/gradle/wrapper/gradle-wrapper.properties" ]; then
        GRADLE_VERSION=$(grep "distributionUrl" "$PROJECT_ROOT/gradle/wrapper/gradle-wrapper.properties" | sed -n 's/.*gradle-\(.*\)-bin.zip/\1/p')
        log_info "检测到项目 Gradle 版本: $GRADLE_VERSION"
    else
        GRADLE_VERSION="8.13"
        log_warning "未找到 gradle-wrapper.properties，使用默认 Gradle 版本: $GRADLE_VERSION"
    fi

    # 读取 compileSdk 和 buildTools 版本
    if [ -f "$PROJECT_ROOT/app/build.gradle.kts" ]; then
        COMPILE_SDK=$(grep "compileSdk" "$PROJECT_ROOT/app/build.gradle.kts" | grep -o '[0-9]\+' | head -1)
        log_info "检测到项目 compileSdk: $COMPILE_SDK"
    else
        COMPILE_SDK="36"
        log_warning "未找到 build.gradle.kts，使用默认 compileSdk: $COMPILE_SDK"
    fi

    # 读取 AGP (Android Gradle Plugin) 版本
    if [ -f "$PROJECT_ROOT/gradle/libs.versions.toml" ]; then
        AGP_VERSION=$(grep "agp = " "$PROJECT_ROOT/gradle/libs.versions.toml" | sed -n 's/.*"\(.*\)".*/\1/p')
        log_info "检测到项目 AGP 版本: $AGP_VERSION"
    else
        AGP_VERSION="8.13.0"
        log_warning "未找到 libs.versions.toml，使用默认 AGP 版本: $AGP_VERSION"
    fi

    # 根据 AGP 版本确定所需的 JDK 版本
    # AGP 8.x 需要 JDK 17
    local agp_major=$(echo "$AGP_VERSION" | cut -d'.' -f1)
    if [ "$agp_major" -ge 8 ]; then
        REQUIRED_JDK_VERSION="17"
    else
        REQUIRED_JDK_VERSION="11"
    fi
    log_info "项目需要 JDK 版本: $REQUIRED_JDK_VERSION"

    # 确定 build-tools 版本（通常与 compileSdk 对应）
    BUILD_TOOLS_VERSION="${COMPILE_SDK}.0.0"
    log_info "将安装 build-tools 版本: $BUILD_TOOLS_VERSION"
}

# Git 代码拉取功能
git_clone_project() {
    log_git "开始 Git 代码拉取..."
    
    # 检查 git 是否安装
    if ! command -v git &> /dev/null; then
        log_error "未找到 git 命令，请先安装 Git"
        exit 1
    fi
    
    # 交互式获取 Git 仓库信息
    local git_url=""
    local git_username=""
    local git_password=""
    local target_dir=""
    local use_credentials=false
    
    # 获取 Git 仓库 URL
    if [ -z "$GIT_REPO_URL" ]; then
        echo ""
        log_git "请输入 Git 仓库地址 (例如: https://github.com/user/repo.git):"
        read -r git_url
    else
        git_url="$GIT_REPO_URL"
    fi
    
    if [ -z "$git_url" ]; then
        log_error "Git 仓库地址不能为空"
        exit 1
    fi
    
    log_info "Git 仓库: $git_url"
    
    # 检查是否需要认证
    case "$git_url" in
        https://*)
            echo ""
            log_git "是否需要提供 Git 账号密码? (y/n，默认: n):"
            read -r need_auth
            
            if [ "$need_auth" = "y" ] || [ "$need_auth" = "Y" ]; then
                use_credentials=true
            
            # 获取用户名
            if [ -z "$GIT_USERNAME" ]; then
                log_git "请输入 Git 用户名:"
                read -r git_username
            else
                git_username="$GIT_USERNAME"
            fi
            
            # 获取密码（不显示输入）
            if [ -z "$GIT_PASSWORD" ]; then
                log_git "请输入 Git 密码或 Personal Access Token:"
                read -s git_password
                echo ""
            else
                git_password="$GIT_PASSWORD"
            fi
            
            if [ -z "$git_username" ] || [ -z "$git_password" ]; then
                log_error "用户名和密码不能为空"
                exit 1
            fi
            fi
            ;;
    esac
    
    # 确定目标目录
    local repo_name=$(basename "$git_url" .git)
    target_dir="$PROJECT_ROOT/$repo_name"
    
    echo ""
    log_git "目标目录: $target_dir"
    
    # 检查目标目录是否已存在
    if [ -d "$target_dir" ]; then
        log_warning "目标目录已存在: $target_dir"
        log_git "是否删除并重新克隆? (y/n，默认: n):"
        read -r confirm_delete
        
        if [ "$confirm_delete" = "y" ] || [ "$confirm_delete" = "Y" ]; then
            log_git "删除现有目录..."
            rm -rf "$target_dir"
        else
            log_git "是否执行 git pull 更新代码? (y/n，默认: y):"
            read -r confirm_pull
            
            if [ "$confirm_pull" != "n" ] && [ "$confirm_pull" != "N" ]; then
                log_git "更新代码..."
                cd "$target_dir"
                
                if [ "$use_credentials" = true ]; then
                    # 使用凭据更新
                    git -c credential.helper='!f() { echo "username=${git_username}"; echo "password=${git_password}"; }; f' pull
                else
                    git pull
                fi
                
                log_success "代码更新完成"
                cd "$PROJECT_ROOT"
                return 0
            else
                log_info "跳过更新"
                return 0
            fi
        fi
    fi
    
    # 克隆仓库
    log_git "开始克隆仓库..."
    
    if [ "$use_credentials" = true ]; then
        # 构建带凭据的 URL
        local protocol=$(echo "$git_url" | sed -n 's/\(https\?:\/\/\).*/\1/p')
        local url_without_protocol=$(echo "$git_url" | sed "s|$protocol||")
        local auth_url="${protocol}${git_username}:${git_password}@${url_without_protocol}"
        
        # 使用 credential helper 避免密码泄露到命令行
        GIT_TERMINAL_PROMPT=0 git -c credential.helper='!f() { echo "username=${git_username}"; echo "password=${git_password}"; }; f' clone "$git_url" "$target_dir"
    else
        # 不使用凭据克隆
        git clone "$git_url" "$target_dir"
    fi
    
    if [ $? -eq 0 ]; then
        log_success "代码克隆完成"
        log_info "项目位置: $target_dir"
        
        # 显示仓库信息
        cd "$target_dir"
        local current_branch=$(git branch --show-current)
        local commit_hash=$(git rev-parse --short HEAD)
        local commit_message=$(git log -1 --pretty=%B | head -n 1)
        
        echo ""
        log_info "=== 仓库信息 ==="
        log_info "分支: $current_branch"
        log_info "提交: $commit_hash"
        log_info "消息: $commit_message"
        
        cd "$PROJECT_ROOT"
    else
        log_error "代码克隆失败"
        exit 1
    fi
}

# 下载并安装 JDK
download_and_setup_jdk() {
    log_setup "开始下载和配置 JDK..."

    # 创建本地环境目录
    mkdir -p "$LOCAL_ENV_DIR"

    # 获取项目所需的 JDK 版本
    get_project_versions

    # 确定 JDK 下载链接
    local jdk_url=""
    local jdk_filename=""

    case "$OS_TYPE" in
        "macos")
            if [ "$ARCH_TYPE" = "arm64" ]; then
                jdk_url="https://download.oracle.com/java/${REQUIRED_JDK_VERSION}/archive/jdk-${REQUIRED_JDK_VERSION}.0.12_macos-aarch64_bin.tar.gz"
                jdk_filename="openjdk-${REQUIRED_JDK_VERSION}-macos-aarch64.tar.gz"
            else
                jdk_url="https://download.oracle.com/java/${REQUIRED_JDK_VERSION}/archive/jdk-${REQUIRED_JDK_VERSION}.0.12_macos-x64_bin.tar.gz"
                jdk_filename="openjdk-${REQUIRED_JDK_VERSION}-macos-x64.tar.gz"
            fi
            ;;
        "linux")
            if [ "$ARCH_TYPE" = "arm64" ]; then
                jdk_url="https://download.oracle.com/java/${REQUIRED_JDK_VERSION}/archive/jdk-${REQUIRED_JDK_VERSION}.0.12_linux-aarch64_bin.tar.gz"
                jdk_filename="openjdk-${REQUIRED_JDK_VERSION}-linux-aarch64.tar.gz"
            else
                jdk_url="https://download.oracle.com/java/${REQUIRED_JDK_VERSION}/archive/jdk-${REQUIRED_JDK_VERSION}.0.12_linux-x64_bin.tar.gz"
                jdk_filename="openjdk-${REQUIRED_JDK_VERSION}-linux-x64.tar.gz"
            fi
            ;;
        "windows")
            jdk_url="https://download.oracle.com/java/${REQUIRED_JDK_VERSION}/archive/jdk-${REQUIRED_JDK_VERSION}.0.12_windows-x64_bin.zip"
            jdk_filename="openjdk-${REQUIRED_JDK_VERSION}-windows-x64.zip"
            ;;
        *)
            log_error "不支持的操作系统: $OS_TYPE"
            exit 1
            ;;
    esac

    local jdk_archive="$LOCAL_ENV_DIR/$jdk_filename"

    # 下载 JDK
    if [ ! -f "$jdk_archive" ]; then
        download_with_progress "$jdk_url" "$jdk_archive" "JDK 17"
    else
        log_info "JDK 安装包已存在，跳过下载"
    fi

    # 解压 JDK
    if [ ! -d "$LOCAL_JDK_DIR" ]; then
        log_setup "解压 JDK..."
        mkdir -p "$LOCAL_JDK_DIR"

        case "$jdk_filename" in
            *.tar.gz)
                tar -xzf "$jdk_archive" -C "$LOCAL_JDK_DIR" --strip-components=1
                ;;
            *.zip)
                if command -v unzip &> /dev/null; then
                    unzip -q "$jdk_archive" -d "$LOCAL_ENV_DIR/temp_jdk"
                    mv "$LOCAL_ENV_DIR/temp_jdk"/*/* "$LOCAL_JDK_DIR/"
                    rm -rf "$LOCAL_ENV_DIR/temp_jdk"
                else
                    log_error "未找到 unzip 命令，无法解压 ZIP 文件"
                    exit 1
                fi
                ;;
        esac

        log_success "JDK 解压完成"
    else
        log_info "JDK 已解压，跳过解压步骤"
    fi

    # 设置 JAVA_HOME 和 PATH
    export JAVA_HOME="$LOCAL_JDK_DIR"
    export PATH="$JAVA_HOME/bin:$PATH"

    log_success "JDK 配置完成"
    log_info "JAVA_HOME: $JAVA_HOME"
}

# 下载并安装 Android SDK
download_and_setup_android_sdk() {
    log_setup "开始下载和配置 Android SDK..."

    # 创建本地环境目录
    mkdir -p "$LOCAL_ENV_DIR"

    # 确定 Android SDK Command Line Tools 下载链接
    local sdk_url=""
    local sdk_filename=""

    case "$OS_TYPE" in
        "macos")
            sdk_url="https://dl.google.com/android/repository/commandlinetools-mac-11076708_latest.zip"
            sdk_filename="commandlinetools-mac-latest.zip"
            ;;
        "linux")
            sdk_url="https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip"
            sdk_filename="commandlinetools-linux-latest.zip"
            ;;
        "windows")
            sdk_url="https://dl.google.com/android/repository/commandlinetools-win-11076708_latest.zip"
            sdk_filename="commandlinetools-win-latest.zip"
            ;;
        *)
            log_error "不支持的操作系统: $OS_TYPE"
            exit 1
            ;;
    esac

    local sdk_archive="$LOCAL_ENV_DIR/$sdk_filename"

    # 下载 Android SDK Command Line Tools
    if [ ! -f "$sdk_archive" ]; then
        download_with_progress "$sdk_url" "$sdk_archive" "Android SDK Command Line Tools"
    else
        log_info "Android SDK 安装包已存在，跳过下载"
    fi

    # 解压 Android SDK
    if [ ! -d "$LOCAL_SDK_DIR" ]; then
        log_setup "解压 Android SDK..."
        mkdir -p "$LOCAL_SDK_DIR"

        if command -v unzip &> /dev/null; then
            unzip -q "$sdk_archive" -d "$LOCAL_SDK_DIR"
            # 移动到正确的目录结构
            mv "$LOCAL_SDK_DIR/cmdline-tools" "$LOCAL_SDK_DIR/cmdline-tools-temp"
            mkdir -p "$LOCAL_SDK_DIR/cmdline-tools/latest"
            mv "$LOCAL_SDK_DIR/cmdline-tools-temp"/* "$LOCAL_SDK_DIR/cmdline-tools/latest/"
            rm -rf "$LOCAL_SDK_DIR/cmdline-tools-temp"
        else
            log_error "未找到 unzip 命令，无法解压 Android SDK"
            exit 1
        fi

        log_success "Android SDK 解压完成"
    else
        log_info "Android SDK 已解压，跳过解压步骤"
    fi

    # 设置 Android SDK 环境变量
    export ANDROID_HOME="$LOCAL_SDK_DIR"
    export ANDROID_SDK_ROOT="$LOCAL_SDK_DIR"
    export PATH="$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$PATH"

    # 安装必要的 SDK 组件
    log_setup "安装必要的 Android SDK 组件..."

    # 根据操作系统选择正确的 sdkmanager 命令
    local sdkmanager_cmd="sdkmanager"
    if [ "$OS_TYPE" = "windows" ] || [ -n "$MSYSTEM" ]; then
        sdkmanager_cmd="sdkmanager.bat"
    fi

    # 接受许可证
    log_setup "接受 Android SDK 许可证..."
    if [ "$OS_TYPE" = "windows" ] || [ -n "$MSYSTEM" ]; then
        # Windows Git Bash 环境，使用 echo 来自动确认
        echo -e "y\ny\ny\ny\ny\ny\ny\ny\ny\ny\ny\ny\ny\ny\ny\ny\ny\ny\ny\ny" | "$sdkmanager_cmd" --licenses >/dev/null 2>&1 || true
    else
        # Unix/Linux/macOS 环境
        yes | "$sdkmanager_cmd" --licenses >/dev/null 2>&1 || true
    fi
    log_success "许可证接受完成"

    # 获取项目版本信息（如果还没有获取）
    if [ -z "$COMPILE_SDK" ]; then
        get_project_versions
    fi

    # 安装基本组件（使用项目实际需要的版本）
    log_info "安装 SDK 组件: platform-tools, platforms;android-${COMPILE_SDK}, build-tools;${BUILD_TOOLS_VERSION}"
    "$sdkmanager_cmd" "platform-tools" "platforms;android-${COMPILE_SDK}" "build-tools;${BUILD_TOOLS_VERSION}" --channel=0

    log_success "Android SDK 配置完成"
    log_info "ANDROID_HOME: $ANDROID_HOME"
}

# 创建签名文件
create_keystore() {
    log_info "创建签名文件..."
    echo ""
    
    # 检查 keytool 是否可用
    if ! command -v keytool &> /dev/null; then
        # 尝试使用本地 JDK
        if [ -d "$LOCAL_JDK_DIR" ]; then
            export JAVA_HOME="$LOCAL_JDK_DIR"
            export PATH="$JAVA_HOME/bin:$PATH"
        fi
        
        if ! command -v keytool &> /dev/null; then
            log_error "未找到 keytool 命令，请先安装 JDK 或运行: $0 download-env"
            exit 1
        fi
    fi
    
    # 选择渠道
    echo "选择要创建签名的渠道:"
    echo "  1) Play 市场 (playstore)"
    echo "  2) 内部测试 (internal)"
    echo "  3) 两个渠道都创建"
    echo "  4) 自定义目录"
    printf "请选择 [1-4]: "
    read channel_choice
    
    case "$channel_choice" in
        1) channels="playstore" ;;
        2) channels="internal" ;;
        3) channels="playstore internal" ;;
        4) 
            printf "请输入目录路径 (相对于项目根目录，如 app/src/prod): "
            read custom_dir
            channels="CUSTOM:$custom_dir"
            ;;
        *) log_error "无效选择"; exit 1 ;;
    esac
    
    for channel in $channels; do
        echo ""
        
        # 处理自定义目录
        local keystore_dir
        local display_name
        case "$channel" in
            CUSTOM:*)
                keystore_dir="$PROJECT_ROOT/${channel#CUSTOM:}"
                display_name="自定义目录"
                ;;
            *)
                keystore_dir="$PROJECT_ROOT/app/src/$channel"
                display_name="$channel"
                ;;
        esac
        
        log_info "=== 创建 $display_name 渠道签名 ==="
        
        # 询问签名文件名
        printf "签名文件名 [默认: release.keystore]: "
        read keystore_name
        keystore_name="${keystore_name:-release.keystore}"
        
        # 确保文件名有正确的扩展名
        case "$keystore_name" in
            *.keystore|*.jks) ;;
            *) keystore_name="${keystore_name}.keystore" ;;
        esac
        
        local keystore_file="$keystore_dir/$keystore_name"
        
        # 检查目录是否存在
        if [ ! -d "$keystore_dir" ]; then
            log_info "创建目录: $keystore_dir"
            mkdir -p "$keystore_dir"
        fi
        
        # 检查签名文件是否已存在
        if [ -f "$keystore_file" ]; then
            log_warning "签名文件已存在: $keystore_file"
            printf "是否覆盖? (y/n): "
            read overwrite
            if [ "$overwrite" != "y" ] && [ "$overwrite" != "Y" ]; then
                log_info "跳过 $channel 渠道"
                continue
            fi
            rm -f "$keystore_file"
        fi
        
        # 收集签名信息
        echo ""
        log_info "请输入签名信息 (按 Enter 使用默认值):"
        echo ""
        
        printf "密钥库密码 [默认: android]: "
        read store_pass
        store_pass="${store_pass:-android}"
        
        printf "密钥别名 [默认: key0]: "
        read key_alias
        key_alias="${key_alias:-key0}"
        
        printf "密钥密码 [默认: 与密钥库密码相同]: "
        read key_pass
        key_pass="${key_pass:-$store_pass}"
        
        printf "有效期(年) [默认: 25]: "
        read validity_years
        validity_years="${validity_years:-25}"
        local validity_days=$((validity_years * 365))
        
        echo ""
        log_info "请输入证书信息:"
        
        printf "姓名 (CN) [默认: Developer]: "
        read cn
        cn="${cn:-Developer}"
        
        printf "组织单位 (OU) [默认: Development]: "
        read ou
        ou="${ou:-Development}"
        
        printf "组织 (O) [默认: Company]: "
        read o
        o="${o:-Company}"
        
        printf "城市 (L) [默认: City]: "
        read l
        l="${l:-City}"
        
        printf "省份 (ST) [默认: State]: "
        read st
        st="${st:-State}"
        
        printf "国家代码 (C) [默认: CN]: "
        read c
        c="${c:-CN}"
        
        # 构建 DN
        local dname="CN=$cn, OU=$ou, O=$o, L=$l, ST=$st, C=$c"
        
        echo ""
        log_info "正在创建签名文件..."
        log_info "文件: $keystore_file"
        log_info "别名: $key_alias"
        log_info "DN: $dname"
        echo ""
        
        # 创建签名文件
        keytool -genkeypair \
            -v \
            -keystore "$keystore_file" \
            -alias "$key_alias" \
            -keyalg RSA \
            -keysize 2048 \
            -validity "$validity_days" \
            -storepass "$store_pass" \
            -keypass "$key_pass" \
            -dname "$dname"
        
        if [ $? -eq 0 ]; then
            log_success "签名文件创建成功: $keystore_file"
            echo ""
            log_info "请在对应的 config.gradle 或 build.gradle 中配置以下信息:"
            echo "  storeFile = file('$keystore_name')"
            echo "  storePassword = '$store_pass'"
            echo "  keyAlias = '$key_alias'"
            echo "  keyPassword = '$key_pass'"
        else
            log_error "签名文件创建失败"
        fi
    done
    
    echo ""
    log_success "签名文件创建完成！"
}

# 显示帮助信息
show_help() {
    echo "Android 项目打包脚本"
    echo ""
    echo "用法:"
    echo "  $0 [模式] [选项]"
    echo ""
    echo "模式:"
    echo "  git-clone                 从 Git 仓库克隆代码到脚本根目录"
    echo "  download-env              仅下载环境（JDK 和 Android SDK）"
    echo "  setup-env                 仅设置环境变量（不下载）"
    echo "  create-key                创建签名文件 (keystore)"
    echo "  build                     执行构建（默认模式）"
    echo "  gradle [命令]             直接执行 gradle 命令"
    echo ""
    echo "Git 克隆选项（用于 git-clone 模式）:"
    echo "  --git-url URL             Git 仓库地址（可选，不提供则交互式输入）"
    echo "  --git-user USERNAME       Git 用户名（可选）"
    echo "  --git-pass PASSWORD       Git 密码或 Token（可选）"
    echo ""
    echo "选项:"
    echo "  -h, --help                显示帮助信息"
    echo "  -c, --channel CHANNEL     指定渠道名称或 'all'，默认: all"
    echo "  -b, --build-type TYPE     指定构建类型 (debug|release|all)，默认: release"
    echo "  -o, --output DIR          指定输出目录，默认: ./outputs"
    echo "  --clean                   构建前清理项目"
    echo "  --bundle                  同时生成 AAB 包"
    echo "  --aab-only                仅构建 AAB 包（不构建 APK）"
    echo "  --no-lint                 跳过 Lint 检查"
    echo "  --skip-sign-check         跳过签名文件检查"
    echo ""
    echo "渠道说明:"
    echo "  渠道名称对应 app/src/ 下的目录名和 productFlavors 配置"
    echo "  默认渠道: $DEFAULT_CHANNELS"
    echo "  all - 构建所有配置的渠道"
    echo ""
    echo "  可通过 .build_channels 文件自定义渠道列表 (每行一个渠道名)"
    echo ""
    echo "示例:"
    echo "  $0 git-clone              # 交互式克隆 Git 仓库"
    echo "  $0 download-env           # 仅下载 JDK 和 Android SDK"
    echo "  $0 setup-env              # 仅设置环境变量"
    echo "  $0 create-key             # 交互式创建签名文件"
    echo "  $0                        # 构建所有渠道的 release 版本"
    echo "  $0 build -c playstore -b release   # 构建指定渠道"
    echo "  $0 build -c prod -b release        # 构建自定义渠道 (需在 build.gradle 中配置)"
    echo "  $0 build --clean --bundle          # 清理后构建，并生成 AAB 包"
    echo "  $0 build --skip-sign-check         # 跳过签名检查直接构建"
    echo "  $0 gradle tasks           # 执行 gradle tasks 命令"
    echo "  $0 gradle assembleDebug   # 执行 gradle assembleDebug 命令"
    echo "  $0 gradle bundleProdRelease        # 构建自定义渠道 AAB"
}

# 确保 SDK 许可证已接受
ensure_sdk_licenses_accepted() {
    log_setup "检查并接受 Android SDK 许可证..."

    # 根据操作系统选择正确的 sdkmanager 命令
    local sdkmanager_cmd="sdkmanager"
    if [ "$OS_TYPE" = "windows" ] || [ -n "$MSYSTEM" ]; then
        sdkmanager_cmd="sdkmanager.bat"
    fi

    # 检查 sdkmanager 是否可用
    if ! command -v "$sdkmanager_cmd" &> /dev/null; then
        log_warning "未找到 sdkmanager，跳过许可证检查"
        return
    fi

    # 接受许可证
    if [ "$OS_TYPE" = "windows" ] || [ -n "$MSYSTEM" ]; then
        # Windows Git Bash 环境，使用 echo 来自动确认
        echo -e "y\ny\ny\ny\ny\ny\ny\ny\ny\ny\ny\ny\ny\ny\ny\ny\ny\ny\ny\ny" | "$sdkmanager_cmd" --licenses >/dev/null 2>&1 || true
    else
        # Unix/Linux/macOS 环境
        yes | "$sdkmanager_cmd" --licenses >/dev/null 2>&1 || true
    fi

    log_success "SDK 许可证检查完成"
}

# 仅下载环境（不设置环境变量）
download_environment() {
    log_info "开始下载构建环境..."
    
    # 检测系统信息
    detect_system
    
    # 下载 JDK
    if [ ! -d "$LOCAL_JDK_DIR" ]; then
        download_and_setup_jdk
    else
        log_info "本地 JDK 已存在: $LOCAL_JDK_DIR"
    fi
    
    # 下载 Android SDK
    if [ ! -d "$LOCAL_SDK_DIR" ]; then
        download_and_setup_android_sdk
    else
        log_info "本地 Android SDK 已存在: $LOCAL_SDK_DIR"
    fi
    
    log_success "环境下载完成"
}

# 仅设置环境变量（不下载）
setup_environment_variables() {
    log_info "设置环境变量..."
    
    # 检测系统信息
    detect_system
    
    # 设置 JAVA_HOME
    if [ -d "$LOCAL_JDK_DIR" ]; then
        export JAVA_HOME="$LOCAL_JDK_DIR"
        export PATH="$JAVA_HOME/bin:$PATH"
        log_success "JAVA_HOME: $JAVA_HOME"
    elif command -v java &> /dev/null; then
        log_info "使用系统 Java"
    else
        log_error "未找到 JDK，请先运行: $0 download-env"
        exit 1
    fi
    
    # 设置 Android SDK
    if [ -d "$LOCAL_SDK_DIR" ]; then
        export ANDROID_HOME="$LOCAL_SDK_DIR"
        export ANDROID_SDK_ROOT="$LOCAL_SDK_DIR"
        export PATH="$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$PATH"
        log_success "ANDROID_HOME: $ANDROID_HOME"
    elif [ -n "$ANDROID_HOME" ] && [ -d "$ANDROID_HOME" ]; then
        log_info "使用系统 Android SDK: $ANDROID_HOME"
    else
        log_error "未找到 Android SDK，请先运行: $0 download-env"
        exit 1
    fi
    
    # 设置 Gradle 命令
    GRADLEW_CMD="./gradlew"
    if [ "$OS_TYPE" = "windows" ] || [ -n "$MSYSTEM" ]; then
        if [ -f "./gradlew.bat" ]; then
            GRADLEW_CMD="./gradlew.bat"
        fi
    fi
    
    log_success "环境变量设置完成"
}

# 智能环境检查和设置
setup_environment() {
    log_info "检查和设置构建环境..."

    # 检测系统信息
    detect_system

    # 获取项目版本信息
    get_project_versions

    # 检查是否需要下载 JDK
    local java_available=false
    local java_version_ok=false

    if command -v java &> /dev/null; then
        java_available=true
        # 检查 Java 版本
        JAVA_VERSION=$(java -version 2>&1 | head -n 1 | cut -d'"' -f2 | cut -d'.' -f1)
        if [ "$JAVA_VERSION" -ge "$REQUIRED_JDK_VERSION" ]; then
            java_version_ok=true
            log_success "找到合适的 Java 版本: $JAVA_VERSION (需要 $REQUIRED_JDK_VERSION+)"
        else
            log_warning "Java 版本过低: $JAVA_VERSION (需要 $REQUIRED_JDK_VERSION+)"
        fi
    else
        log_warning "未找到系统 Java"
    fi

    # 如果没有合适的 JDK，下载本地 JDK
    if [ "$java_available" = false ] || [ "$java_version_ok" = false ]; then
        log_info "需要设置本地 JDK 环境"
        if [ ! -d "$LOCAL_JDK_DIR" ]; then
            download_and_setup_jdk
        else
            log_info "本地 JDK 已存在，使用本地 JDK"
            export JAVA_HOME="$LOCAL_JDK_DIR"
            export PATH="$JAVA_HOME/bin:$PATH"
            log_info "JAVA_HOME: $JAVA_HOME"
        fi
    fi

    # 检查是否需要设置 Android SDK
    local android_sdk_available=false

    if [ -n "$ANDROID_HOME" ] && [ -d "$ANDROID_HOME" ]; then
        android_sdk_available=true
        log_success "找到系统 Android SDK: $ANDROID_HOME"
    elif [ -n "$ANDROID_SDK_ROOT" ] && [ -d "$ANDROID_SDK_ROOT" ]; then
        android_sdk_available=true
        export ANDROID_HOME="$ANDROID_SDK_ROOT"
        log_success "找到系统 Android SDK: $ANDROID_SDK_ROOT"
    else
        log_warning "未找到系统 Android SDK"
    fi

    # 如果没有 Android SDK，下载本地 SDK
    if [ "$android_sdk_available" = false ]; then
        log_info "需要设置本地 Android SDK 环境"
        if [ ! -d "$LOCAL_SDK_DIR" ]; then
            download_and_setup_android_sdk
        else
            log_info "本地 Android SDK 已存在，使用本地 SDK"
            export ANDROID_HOME="$LOCAL_SDK_DIR"
            export ANDROID_SDK_ROOT="$LOCAL_SDK_DIR"
            export PATH="$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$PATH"
            log_info "ANDROID_HOME: $ANDROID_HOME"

            # 确保许可证已接受
            ensure_sdk_licenses_accepted
        fi
    else
        # 即使使用系统 SDK，也要确保许可证已接受
        ensure_sdk_licenses_accepted
    fi

    # 检查 gradlew 并设置跨平台命令
    GRADLEW_CMD="./gradlew"
    if [ "$OS_TYPE" = "windows" ] || [ -n "$MSYSTEM" ]; then
        # Windows Git Bash/MSYS2 环境，优先使用 .bat 版本
        if [ -f "./gradlew.bat" ]; then
            GRADLEW_CMD="./gradlew.bat"
        elif [ -f "./gradlew" ]; then
            GRADLEW_CMD="./gradlew"
        else
            log_error "未找到 gradlew 或 gradlew.bat 文件"
            exit 1
        fi
    else
        # Unix/Linux/macOS 环境
        if [ ! -f "./gradlew" ]; then
            log_error "未找到 gradlew 文件"
            exit 1
        fi
    fi

    # 检查签名文件
    if [ "$SKIP_SIGN_CHECK" = true ]; then
        log_warning "跳过签名文件检查 (--skip-sign-check)"
    else
        log_info "检查签名文件..."
        
        local keystore_missing=false
        local channels_to_check=""
        
        # 确定要检查的渠道
        if [ "$CHANNEL" = "all" ]; then
            channels_to_check="$DEFAULT_CHANNELS"
        else
            channels_to_check="$CHANNEL"
        fi
        
        # 检查每个渠道的签名文件
        for ch in $channels_to_check; do
            local channel_keystore=""
            local channel_dir="./app/src/$ch"
            
            # 检查渠道目录是否存在
            if [ ! -d "$channel_dir" ]; then
                log_warning "渠道目录不存在: $channel_dir (将跳过签名检查)"
                continue
            fi
            
            # 搜索签名文件
            for ks in "$channel_dir/"*.keystore "$channel_dir/"*.jks; do
                if [ -f "$ks" ]; then
                    channel_keystore="$ks"
                    break
                fi
            done
            
            if [ -z "$channel_keystore" ]; then
                log_error "未找到 $ch 渠道签名文件"
                log_error "请在 $channel_dir/ 目录下放置 .keystore 或 .jks 签名文件"
                log_error "或运行 'bash build.sh create-key' 创建签名文件"
                keystore_missing=true
            else
                log_success "$ch 渠道签名文件: $channel_keystore"
            fi

            # Google Services JSON 为可选
            if [ ! -f "$channel_dir/google-services.json" ]; then
                log_warning "未找到 $ch 渠道 google-services.json (可选，Firebase 功能将不可用)"
            fi
        done

        if [ "$keystore_missing" = true ]; then
            log_error "提示: 使用 --skip-sign-check 可跳过签名检查"
            exit 1
        fi

        log_success "签名文件检查通过"
    fi

    # 验证最终环境
    log_info "验证构建环境..."

    if ! command -v java &> /dev/null; then
        log_error "Java 仍然不可用，环境设置失败"
        exit 1
    fi

    FINAL_JAVA_VERSION=$(java -version 2>&1 | head -n 1 | cut -d'"' -f2 | cut -d'.' -f1)
    if [ "$FINAL_JAVA_VERSION" -lt "$REQUIRED_JDK_VERSION" ]; then
        log_error "Java 版本仍然不符合要求: $FINAL_JAVA_VERSION (需要 $REQUIRED_JDK_VERSION+)"
        exit 1
    fi

    if [ -z "$ANDROID_HOME" ]; then
        log_error "ANDROID_HOME 未设置，环境设置失败"
        exit 1
    fi

    log_success "环境设置完成"
    log_info "Java版本: $FINAL_JAVA_VERSION"
    log_info "JAVA_HOME: $JAVA_HOME"
    log_info "ANDROID_HOME: $ANDROID_HOME"
    log_info "Gradle命令: $GRADLEW_CMD"
}

# 旧的环境检查函数，保留作为备用
check_environment_simple() {
    log_info "检查构建环境..."

    # 检查 Java
    if ! command -v java &> /dev/null; then
        log_error "未找到 Java，请安装 JDK 11 或更高版本"
        exit 1
    fi

    # 检查 Java 版本
    JAVA_VERSION=$(java -version 2>&1 | head -n 1 | cut -d'"' -f2 | cut -d'.' -f1)
    if [ "$JAVA_VERSION" -lt 11 ]; then
        log_error "Java 版本过低，需要 JDK 11 或更高版本"
        exit 1
    fi

    # 检查 gradlew
    if [ ! -f "./gradlew" ]; then
        log_error "未找到 gradlew 文件"
        exit 1
    fi

    # 检查 Android SDK (可选，因为可能在 gradle.properties 中配置了)
    if [ -z "$ANDROID_HOME" ] && [ -z "$ANDROID_SDK_ROOT" ]; then
        log_warning "未设置 ANDROID_HOME 或 ANDROID_SDK_ROOT 环境变量"
        log_warning "如果构建失败，请设置 Android SDK 路径"
    fi

    # 检查签名文件
    log_info "检查签名文件..."

    # 检查 Play 市场渠道签名文件
    if [ ! -f "./app/src/playstore/photorec.keystore" ]; then
        log_error "未找到 Play 市场渠道签名文件: ./app/src/playstore/photorec.keystore"
        exit 1
    fi

    if [ ! -f "./app/src/playstore/google-services.json" ]; then
        log_error "未找到 Play 市场渠道配置文件: ./app/src/playstore/google-services.json"
        exit 1
    fi

    # 检查内部测试渠道签名文件
    if [ ! -f "./app/src/internal/internal-release-key.jks" ]; then
        log_error "未找到内部测试渠道签名文件: ./app/src/internal/internal-release-key.jks"
        exit 1
    fi

    if [ ! -f "./app/src/internal/google-services.json" ]; then
        log_error "未找到内部测试渠道配置文件: ./app/src/internal/google-services.json"
        exit 1
    fi

    log_success "所有渠道签名文件检查通过"
}

# 清理项目
clean_project() {
    if [ "$CLEAN_BUILD" = true ]; then
        log_info "清理项目..."
        $GRADLEW_CMD clean
        log_success "项目清理完成"
    fi
}

# 创建输出目录
create_output_dir() {
    if [ ! -d "$OUTPUT_DIR" ]; then
        mkdir -p "$OUTPUT_DIR"
        log_info "创建输出目录: $OUTPUT_DIR"
    fi
}

# 构建 APK
build_apk() {
    local channel=$1
    local build_type=$2
    # 变体名称格式：channel + buildType（首字母大写）
    # 例如：internal + Release = internalRelease, playstore + Debug = playstoreDebug
    # 使用兼容 bash 3.x 的方法将首字母大写
    local first_char=$(echo "${build_type:0:1}" | tr '[:lower:]' '[:upper:]')
    local rest_chars="${build_type:1}"
    local variant="${channel}${first_char}${rest_chars}"

    log_info "构建 $variant APK..."

    # 在R8混淆前激进清理内存
    log_info "激进清理内存为R8混淆做准备..."
    $GRADLEW_CMD --stop > /dev/null 2>&1 || true

    # 清理所有可能占用内存的中间文件
    log_info "清理中间文件..."
    rm -rf ./app/build/tmp/ > /dev/null 2>&1 || true
    rm -rf ./app/build/intermediates/dex* > /dev/null 2>&1 || true
    rm -rf ./app/build/intermediates/transforms/ > /dev/null 2>&1 || true
    rm -rf ./app/build/intermediates/javac/ > /dev/null 2>&1 || true
    rm -rf ./app/build/intermediates/compile_and_runtime_not_namespaced_r_class_jar/ > /dev/null 2>&1 || true
    rm -rf ./.gradle/caches/transforms-* > /dev/null 2>&1 || true

    # 等待内存释放
    log_info "等待内存释放..."
    sleep 5

    # 跳过可能导致OOM的Lint任务，但保持R8混淆
    log_info "开始构建（已跳过Lint任务，优化内存使用）..."
    local lint_skip_args=""
    if [ "$NO_LINT" = true ]; then
        lint_skip_args="-x lint -x lintVitalRelease -x lintVitalDebug"
    fi

    $GRADLEW_CMD assemble$variant \
        $lint_skip_args \
        --max-workers=1 \
        --no-parallel \
        --no-daemon

    # 查找生成的 APK 文件（使用变体名称查找更准确）
    APK_PATH=$(find ./app/build/outputs/apk -name "*.apk" -path "*$variant*" 2>/dev/null | head -1)

    # 如果没找到，尝试使用渠道和构建类型查找
    if [ -z "$APK_PATH" ] || [ ! -f "$APK_PATH" ]; then
        APK_PATH=$(find ./app/build/outputs/apk -name "*.apk" -path "*$channel*" -path "*$build_type*" 2>/dev/null | head -1)
    fi

    if [ -f "$APK_PATH" ]; then
        # 复制到输出目录
        APK_NAME=$(basename "$APK_PATH")
        cp "$APK_PATH" "$OUTPUT_DIR/"
        log_success "APK 构建完成: $APK_NAME"

        # 显示文件信息
        APK_SIZE=$(du -h "$APK_PATH" | cut -f1)
        log_info "文件大小: $APK_SIZE"
        log_info "文件路径: $OUTPUT_DIR/$APK_NAME"
    else
        log_error "未找到生成的 APK 文件"
        return 1
    fi
}

# 构建 AAB
build_bundle() {
    local channel=$1
    local build_type=$2
    # 使用兼容 bash 3.x 的方法将首字母大写
    local first_char=$(echo "${build_type:0:1}" | tr '[:lower:]' '[:upper:]')
    local rest_chars="${build_type:1}"
    local variant="${channel}${first_char}${rest_chars}"

    log_info "构建 $variant AAB..."

    $GRADLEW_CMD bundle$variant

    # 查找生成的 AAB 文件（使用变体名称查找更准确）
    AAB_PATH=$(find ./app/build/outputs/bundle -name "*.aab" -path "*$variant*" 2>/dev/null | head -1)

    # 如果没找到，尝试使用渠道和构建类型查找
    if [ -z "$AAB_PATH" ] || [ ! -f "$AAB_PATH" ]; then
        AAB_PATH=$(find ./app/build/outputs/bundle -name "*.aab" -path "*$channel*" -path "*$build_type*" 2>/dev/null | head -1)
    fi

    if [ -f "$AAB_PATH" ]; then
        # 复制到输出目录
        AAB_NAME=$(basename "$AAB_PATH")
        cp "$AAB_PATH" "$OUTPUT_DIR/"
        log_success "AAB 构建完成: $AAB_NAME"

        # 显示文件信息
        AAB_SIZE=$(du -h "$AAB_PATH" | cut -f1)
        log_info "文件大小: $AAB_SIZE"
        log_info "文件路径: $OUTPUT_DIR/$AAB_NAME"
    else
        log_error "未找到生成的 AAB 文件"
        return 1
    fi
}

# 构建指定变体
build_variant() {
    local channel=$1
    local build_type=$2

    log_info "==== 构建 $channel-$build_type 变体 ===="

    # 根据 AAB_ONLY 决定构建内容
    if [ "$AAB_ONLY" = true ]; then
        # 仅构建 AAB
        build_bundle "$channel" "$build_type"
    else
        # 构建 APK
        build_apk "$channel" "$build_type"

        # 如果需要，构建 AAB
        if [ "$BUILD_BUNDLE" = true ]; then
            build_bundle "$channel" "$build_type"
        fi
    fi

    echo ""
}

# 主构建函数
main_build() {
    local channels=""
    local build_types=""

    # 确定要构建的渠道
    if [ "$CHANNEL" = "all" ]; then
        channels="$DEFAULT_CHANNELS"
    else
        channels="$CHANNEL"
    fi

    # 确定要构建的类型
    if [ "$BUILD_TYPE" = "all" ]; then
        build_types="debug release"
    else
        build_types="$BUILD_TYPE"
    fi

    log_info "开始构建..."
    log_info "渠道: $channels"
    log_info "构建类型: $build_types"
    echo ""

    # 遍历所有组合进行构建
    for channel in $channels; do
        for build_type in $build_types; do
            build_variant "$channel" "$build_type"
        done
    done
}

# 显示构建结果
show_results() {
    log_success "==== 构建完成 ===="
    log_info "输出文件列表:"

    if [ -d "$OUTPUT_DIR" ]; then
        ls -la "$OUTPUT_DIR"/*.apk "$OUTPUT_DIR"/*.aab 2>/dev/null | while read -r line; do
            log_info "$line"
        done
    fi

    log_info "输出目录: $OUTPUT_DIR"
}

# 执行自定义 Gradle 命令
run_gradle_command() {
    local gradle_args="$@"
    
    log_info "执行 Gradle 命令: $GRADLEW_CMD $gradle_args"
    $GRADLEW_CMD $gradle_args
    
    log_success "Gradle 命令执行完成"
}

# 默认参数
MODE="build"  # 默认模式：build
CHANNEL="all"
BUILD_TYPE="release"
OUTPUT_DIR="$PROJECT_ROOT/outputs"
CLEAN_BUILD=false
BUILD_BUNDLE=false
AAB_ONLY=false
NO_LINT=false
SKIP_SIGN_CHECK=false

# Git 相关参数
GIT_REPO_URL=""
GIT_USERNAME=""
GIT_PASSWORD=""

# 解析命令行参数
while [ $# -gt 0 ]; do
    case $1 in
        git-clone)
            MODE="git-clone"
            shift
            ;;
        download-env)
            MODE="download-env"
            shift
            ;;
        setup-env)
            MODE="setup-env"
            shift
            ;;
        create-key)
            MODE="create-key"
            shift
            ;;
        build)
            MODE="build"
            shift
            ;;
        gradle)
            MODE="gradle"
            shift
            GRADLE_ARGS="$@"
            break
            ;;
        --git-url)
            GIT_REPO_URL="$2"
            shift 2
            ;;
        --git-user)
            GIT_USERNAME="$2"
            shift 2
            ;;
        --git-pass)
            GIT_PASSWORD="$2"
            shift 2
            ;;
        -h|--help)
            show_help
            exit 0
            ;;
        -c|--channel)
            CHANNEL="$2"
            # 不再限制渠道名称，允许自定义渠道
            shift 2
            ;;
        -b|--build-type)
            BUILD_TYPE="$2"
            case "$BUILD_TYPE" in
                debug|release|all)
                    ;;
                *)
                    log_error "无效的构建类型: $BUILD_TYPE"
                    log_error "支持的构建类型: debug, release, all"
                    exit 1
                    ;;
            esac
            shift 2
            ;;
        -o|--output)
            OUTPUT_DIR="$2"
            shift 2
            ;;
        --clean)
            CLEAN_BUILD=true
            shift
            ;;
        --bundle)
            BUILD_BUNDLE=true
            shift
            ;;
        --aab-only)
            AAB_ONLY=true
            shift
            ;;
        --no-lint)
            NO_LINT=true
            shift
            ;;
        --skip-sign-check)
            SKIP_SIGN_CHECK=true
            shift
            ;;
        *)
            log_error "未知参数: $1"
            show_help
            exit 1
            ;;
    esac
done

# 主流程
main() {
    case "$MODE" in
        git-clone)
            log_info "模式: Git 代码克隆"
            echo ""
            git_clone_project
            log_success "Git 操作完成！"
            ;;
        download-env)
            log_info "模式: 下载环境"
            echo ""
            download_environment
            log_success "环境下载完成！"
            echo ""
            log_info "提示: 运行 'source <(./build.sh setup-env)' 或重新打开终端以使用新环境"
            ;;
        setup-env)
            # 设置环境变量，所有日志输出到 stderr，export 命令输出到 stdout
            echo -e "${BLUE}[INFO]${NC} 设置环境变量..." >&2
            
            # 设置 JAVA_HOME
            if [ -d "$LOCAL_JDK_DIR" ]; then
                export JAVA_HOME="$LOCAL_JDK_DIR"
                export PATH="$JAVA_HOME/bin:$PATH"
                echo -e "${GREEN}[SUCCESS]${NC} JAVA_HOME: $JAVA_HOME" >&2
            elif command -v java > /dev/null 2>&1; then
                echo -e "${BLUE}[INFO]${NC} 使用系统 Java" >&2
            else
                echo -e "${RED}[ERROR]${NC} 未找到 JDK，请先运行: $0 download-env" >&2
            fi
            
            # 设置 Android SDK
            if [ -d "$LOCAL_SDK_DIR" ]; then
                export ANDROID_HOME="$LOCAL_SDK_DIR"
                export ANDROID_SDK_ROOT="$LOCAL_SDK_DIR"
                export PATH="$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$PATH"
                echo -e "${GREEN}[SUCCESS]${NC} ANDROID_HOME: $ANDROID_HOME" >&2
            elif [ -n "$ANDROID_HOME" ] && [ -d "$ANDROID_HOME" ]; then
                echo -e "${BLUE}[INFO]${NC} 使用系统 Android SDK: $ANDROID_HOME" >&2
            else
                echo -e "${RED}[ERROR]${NC} 未找到 Android SDK，请先运行: $0 download-env" >&2
            fi
            
            echo -e "${GREEN}[SUCCESS]${NC} 环境变量设置完成" >&2
            
            # 只输出纯文本的 export 命令到 stdout
            echo "export JAVA_HOME=\"$JAVA_HOME\""
            echo "export ANDROID_HOME=\"$ANDROID_HOME\""
            echo "export ANDROID_SDK_ROOT=\"$ANDROID_SDK_ROOT\""
            echo "export PATH=\"$PATH\""
            ;;
        create-key)
            log_info "模式: 创建签名文件"
            echo ""
            create_keystore
            ;;
        gradle)
            log_info "模式: 执行 Gradle 命令"
            echo ""
            setup_environment
            run_gradle_command $GRADLE_ARGS
            ;;
        build)
            log_info "模式: 构建项目"
            echo ""
            setup_environment
            create_output_dir
            clean_project
            main_build
            show_results
            log_success "所有构建任务完成！"
            ;;
        *)
            log_error "未知模式: $MODE"
            show_help
            exit 1
            ;;
    esac
}

# 捕获 Ctrl+C
trap 'log_warning "构建被用户中断"; exit 130' INT

# 执行主函数
main