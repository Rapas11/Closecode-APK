#!/usr/bin/env python3
"""
Terminal coding agent: the loop, the tools, and the permission gate.

Setup:
  pip install anthropic
  export ANTHROPIC_API_KEY=...
  python agent.py /path/to/your/android/project

Put the system prompt (terminal-agent-system-prompt-v2.md) next to this file.
Run it inside a container or VM: the path checks below protect the file tools,
but a shell command can still do anything your user account can.
"""
import difflib
import fnmatch
import os
import re
import subprocess
import sys
from datetime import date
from pathlib import Path

import anthropic

MODEL = os.environ.get("AGENT_MODEL", "claude-sonnet-5-5")
MAX_STEPS = 25            # tool rounds per user message before asking to continue
TOKEN_BUDGET = 400_000    # input+output tokens per session before asking to continue
MAX_OUTPUT = 20_000       # characters of tool output sent back to the model
BASH_TIMEOUT = 300        # seconds

ROOT = Path(sys.argv[1] if len(sys.argv) > 1 else ".").resolve()
client = anthropic.Anthropic()

# ---------------------------------------------------------------- state
messages: list = []
mode = "default"          # default | accept-edits | plan
always: set = set()       # categories the user approved for the session
todos: list = []
usage = {"in": 0, "out": 0}

# ---------------------------------------------------------------- prompt
def build_system() -> str:
    text = (Path(__file__).parent / "terminal-agent-system-prompt-v2.md").read_text()
    memory = ROOT / "AGENT.md"
    git = run_shell("git status --short 2>/dev/null | head -20")
    values = {
        "cwd": str(ROOT),
        "is_git_repo": str((ROOT / ".git").exists()),
        "branch": run_shell("git branch --show-current 2>/dev/null").strip() or "n/a",
        "git_status": git.strip() or "clean",
        "platform": sys.platform,
        "shell": os.environ.get("SHELL", "sh"),
        "date": date.today().isoformat(),
        "mode": mode,
        "project_memory": memory.read_text() if memory.exists() else "(none)",
    }
    for key, val in values.items():
        text = text.replace("{{" + key + "}}", val)
    return text


def run_shell(cmd: str, timeout: int = 20) -> str:
    try:
        done = subprocess.run(cmd, shell=True, cwd=ROOT, capture_output=True, text=True, timeout=timeout)
        return done.stdout + done.stderr
    except Exception as exc:
        return str(exc)


# ---------------------------------------------------------------- tool schemas
def schema(name, description, props, required):
    return {"name": name, "description": description,
            "input_schema": {"type": "object", "properties": props, "required": required}}


S = {"type": "string"}
TOOLS = [
    schema("Read", "Read a text file. Optional 1-based line range.",
           {"path": S, "start": {"type": "integer"}, "end": {"type": "integer"}}, ["path"]),
    schema("Glob", "Find files by glob pattern, e.g. **/*.kt", {"pattern": S}, ["pattern"]),
    schema("Grep", "Search file contents with a regular expression.",
           {"pattern": S, "path": S}, ["pattern"]),
    schema("Edit", "Replace exact text in a file. old must match exactly once.",
           {"path": S, "old": S, "new": S}, ["path", "old", "new"]),
    schema("Write", "Create or overwrite a file.", {"path": S, "content": S}, ["path", "content"]),
    schema("Bash", "Run a shell command in the project directory.", {"command": S}, ["command"]),
    schema("TodoWrite", "Replace the todo list. Each item has text and status (pending, in_progress, done).",
           {"items": {"type": "array", "items": {"type": "object", "properties": {
               "text": S, "status": {"type": "string", "enum": ["pending", "in_progress", "done"]}},
               "required": ["text", "status"]}}}, ["items"]),
]

# ---------------------------------------------------------------- safety
SECRET_NAMES = ("local.properties", ".env", "*.jks", "*.keystore", "google-services.json", "*.pem")
RISKY = [r"\brm\s+-[a-z]*r", r"git\s+push\b.*--force", r"git\s+reset\s+--hard", r"git\s+clean\b",
         r"\badb\s+(uninstall|shell\s+pm\s+clear)", r"\bsudo\b", r"curl[^|]*\|\s*(ba)?sh", r"\bmkfs\b", r">\s*/dev/"]


def safe_path(rel: str) -> Path:
    path = (ROOT / rel).resolve()
    if path != ROOT and ROOT not in path.parents:
        raise ValueError(f"Path is outside the project: {rel}")
    return path


def is_secret(path: Path) -> bool:
    return any(fnmatch.fnmatch(path.name, pat) for pat in SECRET_NAMES)


def is_risky(cmd: str) -> bool:
    return any(re.search(pat, cmd) for pat in RISKY)


def clip(text: str) -> str:
    return text if len(text) <= MAX_OUTPUT else text[:MAX_OUTPUT] + f"\n[truncated {len(text) - MAX_OUTPUT} chars]"


def ask(title: str, detail: str, risky: bool = False):
    """Return 1 (yes), 2 (yes always), 3 (no)."""
    print(f"\n\033[33m{title}\033[0m\n{detail}")
    options = ["Yes", "Yes, and don't ask again this session", "No, and tell the agent what to do differently"]
    if risky:
        options = ["Yes (this command is flagged as risky)", None, "No"]
    for i, text in enumerate(options, 1):
        if text:
            print(f"  {i}. {text}")
    while True:
        choice = input("> ").strip()
        if choice in ("1", "3") or (choice == "2" and not risky):
            return int(choice)


# ---------------------------------------------------------------- tool implementations
class Rejected(Exception):
    pass


def tool_read(a):
    path = safe_path(a["path"])
    if is_secret(path):
        return "Refused: this looks like a secrets file."
    lines = path.read_text(errors="replace").splitlines()
    start, end = a.get("start", 1), a.get("end", len(lines))
    return clip("\n".join(f"{n:>5}  {t}" for n, t in enumerate(lines[start - 1:end], start)))


def tool_glob(a):
    hits = sorted(str(p.relative_to(ROOT)) for p in ROOT.glob(a["pattern"])
                  if p.is_file() and not {".git", "build", ".gradle"} & set(p.parts))
    return clip("\n".join(hits[:500]) or "No matches.")


def tool_grep(a):
    regex, base, out = re.compile(a["pattern"]), safe_path(a.get("path", ".")), []
    for p in base.rglob("*"):
        if not p.is_file() or {".git", "build", ".gradle"} & set(p.parts) or is_secret(p):
            continue
        try:
            for n, line in enumerate(p.read_text().splitlines(), 1):
                if regex.search(line):
                    out.append(f"{p.relative_to(ROOT)}:{n}: {line.strip()}")
        except (UnicodeDecodeError, OSError):
            pass
    return clip("\n".join(out[:300]) or "No matches.")


def gate(category: str, key: str, title: str, detail: str, risky=False):
    """Permission check. Raises Rejected with the user's feedback if declined."""
    if mode == "plan":
        raise ValueError("Plan mode is on: no edits or commands. Present a plan and ask for approval.")
    if not risky and (key in always or (mode == "accept-edits" and category == "edit")):
        return
    choice = ask(title, detail, risky)
    if choice == 3:
        feedback = input("What should the agent do differently? ").strip()
        raise Rejected(feedback or "(no feedback given)")
    if choice == 2:
        always.add(key)


def tool_edit(a):
    path = safe_path(a["path"])
    if is_secret(path):
        return "Refused: this looks like a secrets file."
    text = path.read_text()
    if text.count(a["old"]) != 1:
        raise ValueError(f"old text matched {text.count(a['old'])} times; it must match exactly once.")
    new_text = text.replace(a["old"], a["new"], 1)
    diff = "".join(difflib.unified_diff(text.splitlines(True), new_text.splitlines(True),
                                        a["path"], a["path"], n=2))
    gate("edit", "edit", f"Edit {a['path']}?", diff)
    path.write_text(new_text)
    return f"Edited {a['path']}"


def tool_write(a):
    path = safe_path(a["path"])
    if is_secret(path):
        return "Refused: this looks like a secrets file."
    existed = path.exists()
    gate("edit", "edit", f"{'Overwrite' if existed else 'Create'} {a['path']}?",
         f"{len(a['content'].splitlines())} lines")
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(a["content"])
    return f"{'Overwrote' if existed else 'Created'} {a['path']}"


def tool_bash(a):
    cmd = a["command"]
    risky = is_risky(cmd)
    first = cmd.split()[0] if cmd.split() else ""
    gate("bash", f"bash:{first}", "Run this command?", cmd, risky)
    try:
        done = subprocess.run(cmd, shell=True, cwd=ROOT, capture_output=True, text=True, timeout=BASH_TIMEOUT)
        return clip(f"exit code {done.returncode}\n{done.stdout}{done.stderr}")
    except subprocess.TimeoutExpired:
        return f"Command timed out after {BASH_TIMEOUT}s."


def tool_todo(a):
    todos[:] = a["items"]
    marks = {"pending": "[ ]", "in_progress": "[~]", "done": "[x]"}
    print("\n" + "\n".join(f"  {marks[i['status']]} {i['text']}" for i in todos))
    return "Todo list updated."


IMPL = {"Read": tool_read, "Glob": tool_glob, "Grep": tool_grep, "Edit": tool_edit,
        "Write": tool_write, "Bash": tool_bash, "TodoWrite": tool_todo}


def execute(call):
    """Run one tool call. Returns (text, is_error). Raises Rejected."""
    print(f"\033[32m●\033[0m {call.name}({str(call.input)[:100]})")
    try:
        return IMPL[call.name](call.input), False
    except Rejected:
        raise
    except Exception as exc:
        return f"Error: {exc}", True


# ---------------------------------------------------------------- model + loop
def call_model(tools=True):
    kwargs = dict(model=MODEL, max_tokens=8000, system=build_system(), messages=messages, tools=TOOLS)
    if not tools:
        kwargs["tool_choice"] = {"type": "none"}
    resp = client.messages.create(**kwargs)
    usage["in"] += resp.usage.input_tokens
    usage["out"] += resp.usage.output_tokens
    messages.append({"role": "assistant", "content": resp.content})
    for block in resp.content:
        if block.type == "text" and block.text.strip():
            print("\n" + block.text)
    return resp


def tool_calls(resp):
    return [b for b in resp.content if b.type == "tool_use"]


def run_turn(user_text: str):
    messages.append({"role": "user", "content": user_text})
    steps = 0
    while True:
        resp = call_model()
        if resp.stop_reason == "max_tokens":
            print("\n[reply was cut off; ask me to continue]")
            return
        calls = tool_calls(resp)
        if not calls:
            return                                   # model is done: this is the normal exit
        results, rejected = [], None
        for call in calls:
            if rejected is not None:
                results.append(result(call, "Skipped: the user rejected an earlier action.", True))
                continue
            try:
                text, is_error = execute(call)
                results.append(result(call, text, is_error))
            except Rejected as feedback:
                rejected = str(feedback)
                results.append(result(call, "The user rejected this action. Do not retry it or work "
                                            f"around it. Their feedback: {rejected}", True))
        messages.append({"role": "user", "content": results})
        if rejected is not None:
            call_model(tools=False)                  # let the agent respond, no more tools
            return
        steps += 1
        total = usage["in"] + usage["out"]
        if steps >= MAX_STEPS or total >= TOKEN_BUDGET:
            if input(f"\n{steps} steps, {total:,} tokens so far. Keep going? [y/N] ").lower() != "y":
                messages.append({"role": "user", "content": "Stop here and summarize where you are."})
                call_model(tools=False)
                return
            steps = 0
            if total >= TOKEN_BUDGET:
                print("Budget reached; raising it for this session.")
                globals()["TOKEN_BUDGET"] = total + 400_000


def result(call, text, is_error):
    return {"type": "tool_result", "tool_use_id": call.id, "content": text, "is_error": is_error}


def repair():
    """After Ctrl-C, drop a dangling assistant tool request so the history stays valid."""
    while messages and messages[-1]["role"] == "assistant" and any(
            getattr(b, "type", None) == "tool_use" for b in messages[-1]["content"]):
        messages.pop()


# ---------------------------------------------------------------- slash commands
def slash(cmd: str) -> bool:
    global mode
    name, *rest = cmd.split()
    if name == "/help":
        print("/files  /cost  /mode [default|accept-edits|plan]  /compact  /clear  /quit")
    elif name == "/files":
        print(tool_glob({"pattern": "**/*"}))
    elif name == "/cost":
        print(f"{usage['in']:,} input + {usage['out']:,} output tokens")
    elif name == "/mode":
        if rest and rest[0] in ("default", "accept-edits", "plan"):
            mode = rest[0]
        print(f"mode: {mode}")
    elif name == "/clear":
        messages.clear()
        print("Context cleared.")
    elif name == "/compact":
        messages.append({"role": "user", "content": "Summarize our work so far: goals, decisions, files "
                         "changed, failing checks, open questions. Be brief."})
        resp = call_model(tools=False)
        summary = "".join(b.text for b in resp.content if b.type == "text")
        messages[:] = [{"role": "user", "content": "Summary of earlier work:\n" + summary}]
    elif name == "/quit":
        return False
    else:
        print("Unknown command. Type /help.")
    return True


def main():
    print(f"Agent ready in {ROOT}  (mode: {mode}, model: {MODEL}). Type /help.")
    while True:
        try:
            text = input("\n\033[31m>\033[0m ").strip()
            if not text:
                continue
            if text.startswith("/"):
                if not slash(text):
                    break
                continue
            run_turn(text)
        except KeyboardInterrupt:
            repair()
            print("\n[interrupted]")
        except (EOFError, SystemExit):
            break


if __name__ == "__main__":
    main()
