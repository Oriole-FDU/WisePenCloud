import os
import sys
import platform
import shutil
import datetime

# ================= Configuration =================
SERVER_IP = "10.176.44.11"
DOMAIN_NAME = "wisepen-dev-server"
# =================================================

def get_hosts_path():
    """Get the path to the hosts file based on the OS."""
    system = platform.system()
    if system == "Windows":
        return r"C:\Windows\System32\drivers\etc\hosts"
    else:
        # Linux or macOS
        return "/etc/hosts"

def is_admin():
    """Check if the script is running with administrative privileges."""
    try:
        with open(get_hosts_path(), 'a+'):
            pass
        return True
    except PermissionError:
        return False
    except Exception as e:
        print(f"Permission check error: {e}")
        return False

def backup_hosts(hosts_path):
    """Create a timestamped backup of the hosts file."""
    try:
        timestamp = datetime.datetime.now().strftime('%Y%m%d%H%M%S')
        backup_path = f"{hosts_path}.{timestamp}.bak"
        shutil.copy(hosts_path, backup_path)
        print(f"Backup created at: {backup_path}")
        return True
    except Exception as e:
        print(f"Backup failed: {e}")
        return False

def flush_dns():
    """Flush DNS cache based on the OS."""
    print("Attempting to flush DNS cache...")
    try:
        if platform.system() == "Windows":
            os.system("ipconfig /flushdns")
        elif platform.system() == "Darwin": # macOS
            os.system("sudo killall -HUP mDNSResponder")
        print("DNS cache flushed.")
    except Exception as e:
        print(f"Failed to flush DNS: {e}")

def clean_hosts():
    """Remove the configuration from the hosts file."""
    print("Starting cleanup process...")
    hosts_path = get_hosts_path()

    if not is_admin():
        print_permission_error()
        return

    if not backup_hosts(hosts_path):
        return

    try:
        with open(hosts_path, 'r', encoding='utf-8') as f:
            lines = f.readlines()

        new_lines = []
        removed = False

        for line in lines:
            # If the line ends with our domain, skip it (remove it)
            if line.strip().endswith(DOMAIN_NAME):
                removed = True
                print(f"Removing line: {line.strip()}")
            else:
                new_lines.append(line)

        if removed:
            with open(hosts_path, 'w', encoding='utf-8') as f:
                f.writelines(new_lines)
            print(f"Successfully removed configuration for '{DOMAIN_NAME}'.")
            flush_dns()
        else:
            print(f"No configuration found for '{DOMAIN_NAME}'. Nothing to clean.")

    except Exception as e:
        print(f"Cleanup failed: {e}")

def setup_hosts():
    """Add or update the configuration in the hosts file."""
    print("Starting setup process...")
    hosts_path = get_hosts_path()
    entry_line = f"{SERVER_IP} {DOMAIN_NAME}\n"

    if not is_admin():
        print_permission_error()
        return

    if not backup_hosts(hosts_path):
        return

    try:
        with open(hosts_path, 'r', encoding='utf-8') as f:
            lines = f.readlines()

        new_lines = []
        updated = False
        found = False

        for line in lines:
            stripped_line = line.strip()
            if stripped_line.endswith(DOMAIN_NAME):
                found = True
                if stripped_line.startswith(SERVER_IP):
                    print("Configuration already exists and is correct.")
                    new_lines.append(line)
                else:
                    print(f"Updating IP to: {SERVER_IP}...")
                    new_lines.append(entry_line)
                    updated = True
            else:
                new_lines.append(line)

        if not found:
            if new_lines and not new_lines[-1].endswith('\n'):
                new_lines[-1] += '\n'
            new_lines.append(entry_line)
            print(f"Adding new configuration: {entry_line.strip()}")
            updated = True

        if updated or not found:
            with open(hosts_path, 'w', encoding='utf-8') as f:
                f.writelines(new_lines)
            print("Hosts file modified successfully!")
            flush_dns()
            print(f"\nSetup Complete! You can now use '{DOMAIN_NAME}' to connect.")

    except Exception as e:
        print(f"Setup failed: {e}")

def print_permission_error():
    print("\nError: Permission denied!")
    if platform.system() == "Windows":
        print("Please right-click and 'Run as Administrator'.")
    else:
        print("Please run with sudo.")

if __name__ == "__main__":
    # Simple CLI argument parsing
    if len(sys.argv) > 1 and sys.argv[1].lower() == 'clean':
        clean_hosts()
    else:
        setup_hosts()