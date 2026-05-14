import sys
import io
import json
import urllib.request
import urllib.parse

# ─────────────────────────────────────────────────────────────────────────────
# Ghost AI Interface — powered by Pollinations AI (free, no API key required)
# Only this file and PythonBridge.java (encoding fix) were changed.
# ─────────────────────────────────────────────────────────────────────────────

# Re-configure stdout to UTF-8 regardless of Windows console settings.
# This prevents UnicodeEncodeError when AI response contains emojis/special chars,
# which was causing Python to crash silently and Java to read an empty stdout.
sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8", errors="replace", line_buffering=True)

SYSTEM_PROMPT = (
    "You are an educational assistant inside a lab management system called Ghost. "
    "Never provide full code, complete assignments, or direct copy-paste answers. "
    "Instead, provide hints, logic, algorithms, conceptual explanations,"
    "debugging guidance, and small educational examples only. "
    "Encourage students to think and solve problems themselves. "
    "Keep responses concise and friendly."
)

HEADERS = {
    "Content-Type": "application/json",
    "Accept": "application/json",
    "User-Agent": (
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
        "AppleWebKit/537.36 (KHTML, like Gecko) "
        "Chrome/124.0.0.0 Safari/537.36"
    )
}


def try_post_openai(prompt):
    """POST to Pollinations OpenAI-compatible endpoint."""
    url = "https://text.pollinations.ai/openai"
    payload = json.dumps({
        "model": "openai",
        "messages": [
            {"role": "system", "content": SYSTEM_PROMPT},
            {"role": "user",   "content": prompt}
        ]
    }).encode("utf-8")

    req = urllib.request.Request(url, data=payload, headers=HEADERS, method="POST")
    with urllib.request.urlopen(req, timeout=25) as resp:
        body = resp.read().decode("utf-8").strip()
        if not body:
            return None
        result = json.loads(body)
        return result["choices"][0]["message"]["content"].strip()


def try_post_native(prompt):
    """POST to Pollinations native text endpoint (returns plain text)."""
    url = "https://text.pollinations.ai/"
    payload = json.dumps({
        "model": "openai",
        "messages": [
            {"role": "system", "content": SYSTEM_PROMPT},
            {"role": "user",   "content": prompt}
        ],
        "seed": 42
    }).encode("utf-8")

    req = urllib.request.Request(url, data=payload, headers=HEADERS, method="POST")
    with urllib.request.urlopen(req, timeout=25) as resp:
        body = resp.read().decode("utf-8").strip()
        return body if body else None


def try_get(prompt):
    """GET fallback — simplest Pollinations endpoint."""
    encoded = urllib.parse.quote(prompt, safe="")
    sys_enc = urllib.parse.quote(SYSTEM_PROMPT, safe="")
    url = f"https://text.pollinations.ai/{encoded}?model=openai&system={sys_enc}&seed=42"

    req = urllib.request.Request(url, headers={
        "User-Agent": HEADERS["User-Agent"],
        "Accept": "text/plain"
    })
    with urllib.request.urlopen(req, timeout=25) as resp:
        body = resp.read().decode("utf-8").strip()
        return body if body else None


def ask_ai(prompt):
    """Try endpoints in order; always return a non-empty string."""
    attempts = [
        ("POST /openai",  try_post_openai),
        ("POST native",   try_post_native),
        ("GET",           try_get),
    ]

    last_err = "Unknown error"
    for name, fn in attempts:
        try:
            result = fn(prompt)
            if result:
                return result
            print(f"[Ghost AI] {name} returned empty", file=sys.stderr)
        except Exception as e:
            last_err = str(e)
            print(f"[Ghost AI] {name} failed: {e}", file=sys.stderr)

    return (
        "Ghost AI is temporarily unavailable. "
        f"Last error: {last_err}. "
        "Please check your internet connection and try again."
    )


if __name__ == "__main__":
    if len(sys.argv) > 1:
        user_prompt = " ".join(sys.argv[1:])
        result = ask_ai(user_prompt)
        print(result)
        sys.stdout.flush()
    else:
        print("Usage: python ai_interface.py [your question]")
        sys.stdout.flush()
