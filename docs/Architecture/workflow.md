# Team Workflow

## Branch Strategy

- **main:** always stable, production-ready code
- **dev:** integration branch for ongoing development
- **feature/\*** : short-lived branches for each task or issue

## Issues

- Every task must have an Issue in GitLab.
- Use labels:
  - `hardware`
  - `firmware`
  - `ml`
  - `app`
  - `docs`
  - `priority::high`, `priority::medium`, `priority::low`
- Move cards on the Board:  
  **Backlog → To Do → Doing → Review → Done**

## Commit Rules

- Reference Issue number in commit messages:  
  Example:  
  `Implement basic BLE wrapper (#5)`
- One commit = one meaningful change.

## Merge Requests

- Every feature branch must go through a Merge Request (MR).
- MR description should include:
  - What was done
  - Related Issue (e.g., "Closes #7")
  - Testing steps if necessary
- At least one review is recommended before merging to `dev`.

## Meetings

- Weekly check-in meeting.
- Summary added to: `docs/MEETING_NOTES.md`

## Coding Style

- Follow consistent naming, formatting, and folder structure.
- Document decisions in `docs/DECISIONS.md`.

## Goal

Everything should be:

- Traceable
- Measurable
- Reviewable
