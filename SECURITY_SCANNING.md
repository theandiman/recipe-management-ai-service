# Secret Scanning Setup

This repository uses [Gitleaks](https://github.com/gitleaks/gitleaks) to prevent secrets from being committed.

## How It Works

A pre-commit hook automatically scans your staged changes for:
- API keys (Firebase, GCP, Gemini, etc.)
- Private keys and certificates
- Service account credentials
- Tokens and passwords
- Any high-entropy strings that look like secrets

## What Happens When You Commit

```bash
git commit -m "your message"
```

1. The pre-commit hook runs automatically
2. Gitleaks scans your staged files
3. If secrets are found, the commit is blocked
4. If no secrets are found, the commit proceeds

## If a Secret is Detected

```
❌ Secret scanning failed!
🔒 Potential secrets detected in your commit.
```

**What to do:**

1. **Remove the secret** from your code (recommended)
2. **Use environment variables** instead
3. **Add to `.gitleaks.toml` allowlist** if it's a false positive
4. **Use `--no-verify`** to bypass (NOT RECOMMENDED)

## Configuration

The `.gitleaks.toml` file contains:
- Custom rules for Firebase, GCP, and other secrets
- Allowlist for false positives
- Entropy thresholds for detection

## Manual Scan

Scan the entire repository:
```bash
gitleaks detect --no-git
```

Scan staged files only:
```bash
gitleaks protect --staged
```

## Best Practices

✅ **DO:**
- Use environment variables for secrets
- Store secrets in GCP Secret Manager
- Use `.env.example` for documentation
- Keep `.env` in `.gitignore`

❌ **DON'T:**
- Commit `.env` files
- Hardcode API keys in code
- Use `--no-verify` unless absolutely necessary
- Store credentials in version control

## Need Help?

See the [Gitleaks documentation](https://github.com/gitleaks/gitleaks) for more information.
