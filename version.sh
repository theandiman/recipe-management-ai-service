#!/bin/bash
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$SCRIPT_DIR"

cd "$PROJECT_DIR"

# Function to get current version from pom.xml
get_current_version() {
    if [ ! -f "pom.xml" ]; then
        echo "Error: pom.xml not found. Run this script from the project root directory." >&2
        exit 1
    fi
    mvn help:evaluate -Dexpression=project.version -q -DforceStdout
}

# Function to set version using Maven versions plugin
set_version() {
    local new_version=$1
    echo "Setting version to: $new_version"
    mvn versions:set -DnewVersion="$new_version" -DgenerateBackupPoms=false
    echo "Version updated successfully"
}

# Function to bump patch version
bump_patch() {
    local current_version=$(get_current_version)
    # Remove -SNAPSHOT suffix if present
    local base_version=${current_version%-SNAPSHOT}

    # Parse version components
    IFS='.' read -r major minor patch <<< "$base_version"
    local new_patch=$((patch + 1))
    local new_version="$major.$minor.$new_patch"

    set_version "$new_version"
}

# Function to bump minor version
bump_minor() {
    local current_version=$(get_current_version)
    # Remove -SNAPSHOT suffix if present
    local base_version=${current_version%-SNAPSHOT}

    # Parse version components
    IFS='.' read -r major minor patch <<< "$base_version"
    local new_minor=$((minor + 1))
    local new_version="$major.$new_minor.0"

    set_version "$new_version"
}

# Function to bump major version
bump_major() {
    local current_version=$(get_current_version)
    # Remove -SNAPSHOT suffix if present
    local base_version=${current_version%-SNAPSHOT}

    # Parse version components
    IFS='.' read -r major minor patch <<< "$base_version"
    local new_major=$((major + 1))
    local new_version="$new_major.0.0"

    set_version "$new_version"
}

# Function to add SNAPSHOT suffix
add_snapshot() {
    local current_version=$(get_current_version)
    if [[ $current_version != *-SNAPSHOT ]]; then
        set_version "${current_version}-SNAPSHOT"
    else
        echo "Version is already a SNAPSHOT version: $current_version"
    fi
}

# Function to remove SNAPSHOT suffix
remove_snapshot() {
    local current_version=$(get_current_version)
    if [[ $current_version == *-SNAPSHOT ]]; then
        local release_version=${current_version%-SNAPSHOT}
        set_version "$release_version"
    else
        echo "Version is not a SNAPSHOT version: $current_version"
    fi
}

# Main script logic
case "${1:-help}" in
    "current")
        echo "Current version: $(get_current_version)"
        ;;
    "bump-patch")
        bump_patch
        ;;
    "bump-minor")
        bump_minor
        ;;
    "bump-major")
        bump_major
        ;;
    "set-version")
        if [ -z "$2" ]; then
            echo "Error: Please provide a version number"
            echo "Usage: $0 set-version <version>"
            exit 1
        fi
        set_version "$2"
        ;;
    "add-snapshot")
        add_snapshot
        ;;
    "remove-snapshot")
        remove_snapshot
        ;;
    "help"|*)
        echo "Maven Version Management Script"
        echo ""
        echo "Usage: $0 <command> [arguments]"
        echo ""
        echo "Commands:"
        echo "  current                    Show current version"
        echo "  bump-patch                 Bump patch version (1.0.0 -> 1.0.1)"
        echo "  bump-minor                 Bump minor version (1.0.0 -> 1.1.0)"
        echo "  bump-major                 Bump major version (1.0.0 -> 2.0.0)"
        echo "  set-version <version>      Set specific version"
        echo "  add-snapshot               Add SNAPSHOT suffix to current version"
        echo "  remove-snapshot            Remove SNAPSHOT suffix from current version"
        echo "  help                       Show this help message"
        echo ""
        echo "Examples:"
        echo "  $0 current"
        echo "  $0 bump-patch"
        echo "  $0 set-version 2.1.3"
        echo "  $0 add-snapshot"
        ;;
esac