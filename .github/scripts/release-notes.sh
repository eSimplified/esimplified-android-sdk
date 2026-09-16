#!/usr/bin/env bash
set -euo pipefail

version="${1:?usage: release-notes.sh <version> [previous-tag]}"
previous="${2:-}"

if [ -n "$previous" ]; then
    range="${previous}..HEAD"
else
    range="HEAD"
fi

breaking=""
added=""
changed=""
removed=""
fixed=""
other=""

while IFS='|' read -r subject sha; do
    [ -z "$subject" ] && continue

    type=$(printf '%s' "$subject" | sed -n 's/^\([a-z]*\)[(!:].*/\1/p')
    scope=$(printf '%s' "$subject" | sed -n 's/^[a-z]*(\([^)]*\)).*/\1/p')
    description=$(printf '%s' "$subject" | sed 's/^[a-z]*\(([^)]*)\)\{0,1\}!\{0,1\}: *//')
    bang=$(printf '%s' "$subject" | sed -n 's/^[a-z]*\(([^)]*)\)\{0,1\}\(!\):.*/\2/p')

    if [ -z "$type" ]; then
        description="$subject"
    fi

    if [ -n "$scope" ]; then
        entry="- **${scope}:** ${description} (\`${sha}\`)"
    else
        entry="- ${description} (\`${sha}\`)"
    fi

    verb=$(printf '%s' "$description" | cut -d' ' -f1 | tr '[:upper:]' '[:lower:]')

    if [ -n "$bang" ]; then
        breaking="${breaking}${entry}"$'\n'
        continue
    fi

    case "$type" in
        feat|refactor|perf|chore|build)
            case "$verb" in
                remove|removes|removed|drop|drops|dropped|delete|deletes|deleted)
                    removed="${removed}${entry}"$'\n'
                    continue
                    ;;
            esac
            ;;
    esac

    case "$type" in
        feat)
            added="${added}${entry}"$'\n'
            ;;
        fix)
            fixed="${fixed}${entry}"$'\n'
            ;;
        refactor|perf)
            changed="${changed}${entry}"$'\n'
            ;;
        revert)
            removed="${removed}${entry}"$'\n'
            ;;
        *)
            other="${other}${entry}"$'\n'
            ;;
    esac
done < <(git log "$range" --no-merges --format='%s|%h')

section() {
    [ -z "$2" ] && return 0
    printf '### %s\n\n%s\n' "$1" "$2"
}

section '⚠️ Breaking changes' "$breaking"
section 'Added' "$added"
section 'Changed' "$changed"
section 'Removed' "$removed"
section 'Fixed' "$fixed"
section 'Other' "$other"

if [ -z "${breaking}${added}${changed}${removed}${fixed}${other}" ]; then
    printf 'No changes recorded since the previous release.\n\n'
fi

repository="${GITHUB_REPOSITORY:-eSimplified/esimplified-android-sdk}"
if [ -n "$previous" ]; then
    printf '**Full diff:** https://github.com/%s/compare/%s...%s\n' "$repository" "$previous" "$version"
fi
