# Repository Instructions

## Commit messages

Use Conventional Commit messages for every commit in this repository:

```text
<type>(<optional scope>): <description>

<optional body>

<optional footer>
```

- Allowed types: `feat`, `fix`, `refactor`, `perf`, `style`, `test`, `docs`, `build`, `ops`, `chore`.
- Use a scope when it adds useful context; do not use issue identifiers as scopes.
- Write the description in imperative, present tense; keep it lowercase, concise, and without a trailing period.
- Separate body and footer with blank lines. Use the body for motivation and behavior changes.
- Mark breaking changes with `!` before the colon and document them with a `BREAKING CHANGE:` footer.
- Merge and revert commits may use their standard Git message formats.

Before committing, verify the staged diff and use the narrowest accurate type and scope.
