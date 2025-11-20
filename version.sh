#!/bin/bash

# Version management script for recipe-management-ai-service
# Usage: ./version.sh [bump-major|bump-minor|bump-patch|set-version <version>]

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"

cd "$PROJECT_DIR"

# Function to get current version from pom.xml
get_current_version() {
    grep -oP '<revision>\K[^<]+' pom.xml
}

# Function to update version in pom.xml
set_version() {
    local new_version=$1
    sed -i "s|<revision>.*</revision>|<revision>$new_version</revision>|" pom.xml
    echo "Updated version to: $new_version"
}

# Function to bump version
bump_version() {
    local bump_type=$1
    local current_version=$(get_current_version)

    # Remove -SNAPSHOT suffix if present
    local clean_version=${current_version%-SNAPSHOT}

    IFS='.' read -r major minor patch <<< "$clean_version"

    case $bump_type in
        major)
            major=$((major + 1))
            minor=0
            patch=0
            ;;
        minor)
            minor=$((minor + 1))
            patch=0
            ;;
        patch)
            patch=$((patch + 1))
            ;;
        *)
            echo "Invalid bump type. Use: major, minor, or patch"
            exit 1
            ;;
    esac

    local new_version="$major.$minor.$patch"
    set_version "$new_version"
}

# Main logic
case $1 in
    bump-major|bump-minor|bump-patch)
        bump_version "${1#bump-}"
        ;;
    set-version)
        if [ -z "$2" ]; then
            echo "Error: Please provide a version number"
            echo "Usage: $0 set-version <version>"
            exit 1
        fi
        set_version "$2"
        ;;
    current)
        echo "Current version: $(get_current_version)"
        ;;
    *)
        echo "Usage: $0 [bump-major|bump-minor|bump-patch|set-version <version>|current]"
        echo ""
        echo "Examples:"
        echo "  $0 bump-patch          # Bump patch version (1.0.0 -> 1.0.1)"
        echo "  $0 bump-minor          # Bump minor version (1.0.0 -> 1.1.0)"
        echo "  $0 bump-major          # Bump major version (1.0.0 -> 2.0.0)"
        echo "  $0 set-version 2.1.3   # Set specific version"
        echo "  $0 current             # Show current version"
        exit 1
        ;;
esac