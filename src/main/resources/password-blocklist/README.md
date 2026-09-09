# Common-password blocklist

`seclists-10k-most-common.txt` is an unmodified local copy of SecLists' 10,000-entry
common-password list. Password validation performs a whole-entry comparison,
ignoring case with `Locale.ROOT`; it does not trim the candidate, search for
substrings, or change the value that is encoded and stored. No user password is
sent to a third party.

- Upstream project: <https://github.com/danielmiessler/SecLists>
- Upstream file: `Passwords/Common-Credentials/10k-most-common.txt`
- Pinned commit: `a3416ba7062a1bc2ec6b0fe76eac7ee16c80521b`
- Original: <https://raw.githubusercontent.com/danielmiessler/SecLists/a3416ba7062a1bc2ec6b0fe76eac7ee16c80521b/Passwords/Common-Credentials/10k-most-common.txt>
- Downloaded: 2026-09-07.
- SHA-256: `4adb3f0afb4a10cf19ebe48d8c69a46f934bbc8d77c694c210564f9583e7f4ba`
- Upstream license: MIT, copyright Daniel Miessler; the license is reproduced in
  `LICENSE-Seclists.txt` from the same commit.

This is a bounded baseline of common passwords, not a complete compromised-password
database. Most listed entries are also rejected by the independent 15-character
minimum. Refresh the file deliberately when reviewing the registration policy,
preserving upstream provenance, license, checksum, and validation tests. Missing
or empty resources prevent the blocklist bean from starting.
