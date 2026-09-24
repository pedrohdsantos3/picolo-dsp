---
name: pr-workflow
description: Create or update GitHub pull requests with a clear motivation, resolution, and verification record.
---

# Pull request workflow

Use this skill when creating or updating a pull request for this repository.

## Required PR content

Every PR body must explain these three points, using these headings or clear
equivalents:

### Motivation

Describe the problem or opportunity, who or what it affects, and why the change
belongs in this PR.

### Resolution

Summarize the implementation, important design choices, and user-visible or
operational effects. Mention material limitations when they affect review.

### Verification

List the checks actually performed and their results. Name build, test, static
analysis, or manual checks accurately. If a check was not run, say so directly;
never imply success from an unexecuted check. If there is no automated test,
state what evidence was used instead and what remains unverified.

Write the body in the language used for the project discussion unless the user
requests another language. Keep details specific to the diff.

## Creating or updating a PR

1. Identify the exact base and head branches. Review their diff and commit list
   so the PR describes only the changes that will be merged. Keep unrelated
   workspace changes out of the PR.
2. Check whether a PR already exists for the head branch. Reuse the existing PR
   when appropriate; do not create duplicates.
3. Use an action-oriented title and a body with the three required sections.
   Prefer `gh pr create --base <base> --head <head> --title <title>
   --body-file <file>` for multiline bodies.
4. After creation or update, inspect the resulting PR title, base, head, and URL
   and report them to the user.

Only create, edit, or publish a PR when the user requested that external action.
Do not merge or close a PR unless separately requested.
