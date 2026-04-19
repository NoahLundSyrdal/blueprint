# Lessons

- Verify the actual GitHub remote owner/name before calling the GitHub API; forks may differ from assumptions.
- On this machine, prefer /usr/bin/python3 when python or uv are unavailable.
- Preserve unrelated dirty files in the worktree; inspect around them but never stage them unless the task explicitly requires it.
- When GitHub issue APIs return no open issues, verify with both list and search endpoints before concluding the queue is empty.
- When removing legacy phrase handling that is no longer desired, update string-regression tests in the same pass; some tests intentionally assert internal keyword support.
