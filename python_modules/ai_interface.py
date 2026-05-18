import sys
import io
import json
import time
import urllib.request
import urllib.parse

# ─────────────────────────────────────────────────────────────────────────────
# Ghost AI Interface — powered by Pollinations AI (free, no API key required)
# Retry strategy: keeps retrying ALL endpoints in a round-robin loop until
# a real answer comes back.  Never surfaces an error string to the student.
# ─────────────────────────────────────────────────────────────────────────────

# Re-configure stdout to UTF-8 so Windows console encoding never causes a crash.
sys.stdout = io.TextIOWrapper(
    sys.stdout.buffer, encoding="utf-8", errors="replace", line_buffering=True
)

SYSTEM_PROMPT = (
    "You are an educational assistant inside a lab management system called Ghost. "
    "Never provide full code, complete assignments, or direct copy-paste answers. "
    "Instead, provide hints, logic, algorithms, conceptual explanations, "
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
    ),
}

# ── How long (seconds) to wait for each individual HTTP call ─────────────────
REQUEST_TIMEOUT = 45          # generous — slow connections still succeed
# ── How long to pause between failed attempts before retrying ─────────────────
RETRY_DELAYS   = [2, 4, 6, 8, 10]   # seconds; last value repeats forever


# ── Individual endpoint helpers ───────────────────────────────────────────────

def try_post_openai(prompt):
    """POST to Pollinations OpenAI-compatible endpoint."""
    url = "https://text.pollinations.ai/openai"
    payload = json.dumps({
        "model": "openai",
        "messages": [
            {"role": "system", "content": SYSTEM_PROMPT},
            {"role": "user",   "content": prompt},
        ],
    }).encode("utf-8")

    req = urllib.request.Request(url, data=payload, headers=HEADERS, method="POST")
    with urllib.request.urlopen(req, timeout=REQUEST_TIMEOUT) as resp:
        body = resp.read().decode("utf-8").strip()
        if not body:
            return None
        result = json.loads(body)
        return result["choices"][0]["message"]["content"].strip()


def try_post_native(prompt):
    """POST to Pollinations native text endpoint (plain-text response)."""
    url = "https://text.pollinations.ai/"
    payload = json.dumps({
        "model": "openai",
        "messages": [
            {"role": "system", "content": SYSTEM_PROMPT},
            {"role": "user",   "content": prompt},
        ],
        "seed": 42,
    }).encode("utf-8")

    req = urllib.request.Request(url, data=payload, headers=HEADERS, method="POST")
    with urllib.request.urlopen(req, timeout=REQUEST_TIMEOUT) as resp:
        body = resp.read().decode("utf-8").strip()
        return body if body else None


def try_get(prompt):
    """GET fallback — simplest Pollinations endpoint (no POST body needed)."""
    encoded = urllib.parse.quote(prompt, safe="")
    sys_enc = urllib.parse.quote(SYSTEM_PROMPT, safe="")
    url = (
        f"https://text.pollinations.ai/{encoded}"
        f"?model=openai&system={sys_enc}&seed=42"
    )

    req = urllib.request.Request(
        url,
        headers={"User-Agent": HEADERS["User-Agent"], "Accept": "text/plain"},
    )
    with urllib.request.urlopen(req, timeout=REQUEST_TIMEOUT) as resp:
        body = resp.read().decode("utf-8").strip()
        return body if body else None


# ── Endpoints tried in round-robin order ─────────────────────────────────────
ENDPOINTS = [
    ("POST /openai",  try_post_openai),
    ("POST native",   try_post_native),
    ("GET",           try_get),
]


def ask_ai(prompt):
    """
    Keep retrying every endpoint until a real answer is returned.
    This function NEVER returns an error string — only a valid AI answer.
    Errors are written to stderr only (not shown to the student).
    """
    attempt      = 0
    delay_index  = 0

    while True:
        for name, fn in ENDPOINTS:
            try:
                result = fn(prompt)
                if result:
                    return result
                # Empty body — treat as a soft failure and try next endpoint
                print(f"[Ghost AI] {name} returned empty body", file=sys.stderr)
            except Exception as exc:
                print(f"[Ghost AI] {name} failed (attempt {attempt + 1}): {exc}",
                      file=sys.stderr)

            attempt += 1

        # All three endpoints failed in this round — wait before retrying
        delay = RETRY_DELAYS[min(delay_index, len(RETRY_DELAYS) - 1)]
        delay_index += 1
        print(
            f"[Ghost AI] All endpoints failed — retrying in {delay}s "
            f"(total attempts: {attempt})",
            file=sys.stderr,
        )
        time.sleep(delay)


if __name__ == "__main__":
    if len(sys.argv) > 1:
        user_prompt = " ".join(sys.argv[1:])
        result = ask_ai(user_prompt)
        print(result)
        sys.stdout.flush()
    else:
        print("Usage: python ai_interface.py [your question]")
        sys.stdout.flush()
