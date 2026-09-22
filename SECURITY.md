# Security policy

## What belongs here

Please report issues that could expose API keys, medication or health data, bypass the user-confirmation gate, weaken the local-data boundary, send data to an unintended endpoint, or make a network/provider integration unsafe.

Do not include API keys, personal health information, screenshots containing private data, database files, logs with secrets, or exploit details in a public issue or pull request.

## Private reporting

Use GitHub's private [security advisory reporting](https://github.com/freminet-pers/personal-medication-assistant-android/security/advisories/new) for a suspected vulnerability. Include a minimal reproduction, affected commit or release, impact, and a safe way to reproduce without real health data or credentials. If the private form is unavailable, open an issue with only the words “private security contact needed”; do not disclose the vulnerability publicly.

There is no guaranteed response or remediation timeline. Maintainers may ask for a sanitized reproduction and will decide disclosure timing after assessing user impact.

## Out of scope

- Requests for diagnosis, treatment, dose changes, or urgent medical advice. Use a qualified clinician or local emergency service.
- General model quality, unsupported Provider behavior, or a feature request without a security impact.
- Reports that require a user's real key, health record, or unredacted local database.
- Automated scanner output without a reproducible security impact.

## Safe testing

Use mock endpoints, anonymous fixtures, and throwaway keys. Never test against another person's account, a production health system, or a live Provider without explicit authorization. Do not claim a fix is verified until a maintainer can reproduce the check with sanitized data.
