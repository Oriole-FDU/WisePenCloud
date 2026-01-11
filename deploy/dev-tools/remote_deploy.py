import os
import sys
import platform
import subprocess
import random
import argparse

# ================= Configuration =================
SERVER_IP = "wisepen-dev-server"
SERVER_USER = "oriole"
REMOTE_ROOT_DIR = "/tmp"

# 在这里注册你的服务
# key: 服务名称 (用于命令行调用)
# local_dir: 本地资源文件夹 (相对于项目根目录 deploy/)
# entry_script: 上传后在服务器上执行的入口脚本名
SERVICES = {
    "gateway": {
        "local_dir": "gateway-apisix/remote_bundle",
        "entry_script": "setup.sh",
        "desc": "APISIX Gateway Configuration"
    },
    "gateway-patch": {
        "local_dir": "gateway-apisix/remote_bundle",
        "entry_script": "patch.sh",
        "desc": "APISIX Gateway Patch"
    },
    # 以后可以轻松添加新服务
}
# =================================================

def get_project_root():
    """
    获取项目 deploy 文件夹的根目录。
    本脚本位于 deploy/dev-tools/ 下，所以向上找两级。
    """
    script_dir = os.path.dirname(os.path.abspath(__file__))
    return os.path.dirname(script_dir)

def run_command(cmd_list, shell=False):
    """跨平台执行系统命令"""
    try:
        subprocess.run(cmd_list, check=True, text=True, shell=shell)
        return True
    except subprocess.CalledProcessError as e:
        print(f"\n[ERROR] Command failed with exit code {e.returncode}")
        return False
    except FileNotFoundError:
        print(f"\n[ERROR] Command not found (is ssh/scp installed?)")
        return False

def deploy_service(service_name):
    """执行核心部署逻辑"""
    config = SERVICES.get(service_name)
    if not config:
        print(f"[ERROR] Service '{service_name}' not found in configuration.")
        return

    print("==========================================")
    print(f"   Deploying: {service_name}")
    print(f"   Target: {SERVER_USER}@{SERVER_IP}")
    print("==========================================")

    # 路径计算
    project_root = get_project_root()
    local_path = os.path.join(project_root, config['local_dir'])
    entry_script = config['entry_script']

    if not os.path.exists(local_path):
        print(f"[ERROR] Local path does not exist: {local_path}")
        return

    # 准备远程目录
    folder_name = os.path.basename(local_path) # e.g., remote_bundle
    random_id = random.randint(1000, 9999)
    remote_deploy_dir = f"{REMOTE_ROOT_DIR}/deploy_{service_name}_{random_id}"

    # 上传 (SCP)
    print(f"1. Uploading resources to {remote_deploy_dir} ...")
    # scp -r source dest (会在 dest 下创建 source 文件夹)
    scp_cmd = ["scp", "-r", local_path, f"{SERVER_USER}@{SERVER_IP}:{remote_deploy_dir}"]

    if not run_command(scp_cmd):
        return

    # 远程执行 (SSH)
    print(f"2. Executing remote script: {entry_script} ...")

    # 进入目录 -> 赋权 -> 修复换行符 -> 执行 -> 清理
    remote_sh_cmd = (
        f"cd {remote_deploy_dir} &&"
        f"chmod +x {entry_script} && "
        f"sed -i 's/\\r$//' {entry_script} && "
        f"./{entry_script}; "
        f"cd ~ && rm -rf {remote_deploy_dir}"
    )

    ssh_cmd = ["ssh", f"{SERVER_USER}@{SERVER_IP}", remote_sh_cmd]

    if run_command(ssh_cmd):
        print(f"\n[SUCCESS] '{service_name}' deployed successfully!")
    else:
        print(f"\n[FAIL] '{service_name}' deployment failed.")

def show_menu():
    """显示交互式菜单"""
    print("\nAvailable Services:")
    print("-------------------")
    service_keys = list(SERVICES.keys())
    for idx, key in enumerate(service_keys):
        print(f" [{idx + 1}] {key.ljust(15)} - {SERVICES[key]['desc']}")
    print("-------------------")

    try:
        choice = input("Select a service number to deploy: ")
        if choice.isdigit():
            idx = int(choice) - 1
            if 0 <= idx < len(service_keys):
                deploy_service(service_keys[idx])
            else:
                print("Invalid selection.")
        else:
            print("Invalid input.")
    except KeyboardInterrupt:
        print("\nCancelled.")

def pause_if_windows():
    if platform.system() == "Windows":
        print("\nPress Enter to exit...")
        input()

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Universal Remote Deployer")
    parser.add_argument("service", nargs="?", help="Name of the service to deploy (defined in SERVICES)")
    args = parser.parse_args()

    try:
        if args.service:
            deploy_service(args.service)
        else:
            show_menu()
    finally:
        pause_if_windows()