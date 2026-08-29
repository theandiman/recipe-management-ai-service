# Workspace Rules & Directives

## 1. Git & PR Workflow
- Always label Git actions explicitly (*Branch*, *Commit*, *PR*, *Checks*, *Merge*, *Cleanup*).
- Run `gh pr checks <pr>` and confirm all CI checks pass green before merging.
- Run `gh pr view <pr> --comments` and inspect PR feedback before merging.
- Merge via `gh pr merge --squash --delete-branch --admin`, delete local branches, and pull `main`.

## 2. Verification & Licensing
- Keep repository public for free CI minutes while enforcing All Rights Reserved legal protection.
- Build and test via Maven before pushing PRs.
