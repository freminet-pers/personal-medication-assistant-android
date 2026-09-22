# Contributing

Thanks for helping improve this Technical Preview. Contributions should keep the product centered on local personal medication records and reminders; AI remains optional assistance, not the product's medical authority.

## Before opening an issue or pull request

- Do not include personal health information, API keys, tokens, private URLs, databases, APKs, device logs, or screenshots with private data.
- For a possible vulnerability, follow [SECURITY.md](SECURITY.md) instead of posting details publicly.
- For urgent symptoms, medication decisions, diagnosis, or treatment advice, contact a qualified clinician or local emergency service; this repository is not a medical support channel.
- Keep claims tied to evidence: name the device/API, fixture type, command, and exact scope. Mark real API, physical-device, release-signing, and clinical/medical review as unverified unless they were actually performed.

## Scope and review

Documentation, build reproducibility, privacy boundaries, tests, accessibility, and safe error handling are welcome. Avoid changing business behavior in a documentation/governance pull request. Changes that affect medication rules, reminders, data migration, network egress, key handling, or AI prompts need focused maintainer review and updated tests.

If a change makes a medical-safety claim or changes wording about dosage, interactions, contraindications, or when to seek care, maintainers should require an appropriate qualified-domain review before presenting it as guidance. This is a review expectation, not a claim of medical compliance.

## Pull requests

Describe what changed, what did not change, evidence and commands run, and any unresolved risks. Do not use screenshots, API responses, or release assets as proof unless their provenance and redaction status is documented. Keep generated APKs and machine-local files out of commits.
