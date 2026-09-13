import os

directive = """> [!IMPORTANT]
> **AI AGENT DIRECTIVE**
> This document contains strict architectural rules for VYUHA 2.0. Any AI agent or coding assistant operating in this repository must abide by these constraints. Do not deviate, simplify, or hallucinate outside these boundaries. Log any new learnings, corrections, or workflow resolutions to the `.ai/` directory.

"""

print("Applying agent directives to MD files...")
count = 0
for root, dirs, files in os.walk('.'):
    # Exclude specific directories
    if any(skip in root for skip in ['.git', '.agents', '.ai', 'node_modules']):
        continue
    for file in files:
        if file.endswith('.md'):
            path = os.path.join(root, file)
            try:
                with open(path, 'r', encoding='utf-8') as f:
                    content = f.read()
                if '> **AI AGENT DIRECTIVE**' not in content:
                    with open(path, 'w', encoding='utf-8') as f:
                        f.write(directive + content)
                    print(f'Prepended directive to {path}')
                    count += 1
            except Exception as e:
                print(f'Failed to update {path}: {e}')

print(f"Updated {count} markdown files.")

# Create .agents rule
os.makedirs('.agents/rules', exist_ok=True)
rule_content = """# Vyuha 2.0 AI Agent Directives

1. **Documentation is Law**: All Markdown files in `docs/` and `docs/decisions/` are strict architectural invariants. Do not alter architecture, ownership, or module schemas without explicit human approval.
2. **Learning Logs**: Whenever you resolve a complex issue, discover a workaround, or the user corrects your behavior, you MUST document the learning in the `.ai/` directory (e.g., `.ai/learning_log_YYYYMMDD.md`).
"""
with open('.agents/rules/vyuha-directives.md', 'w', encoding='utf-8') as f:
    f.write(rule_content)
print('Created .agents/rules/vyuha-directives.md')

# Create .ai directory
os.makedirs('.ai', exist_ok=True)
ai_readme = """# AI Agent Learning Logs

This directory (`.ai/`) is strictly for AI agents operating in the Vyuha repository.

Whenever an agent makes a significant correction, resolves a complex workflow, or discovers a workaround that other agents should know, it MUST log the finding here.

Format: `.ai/learning_log_YYYYMMDD.md`
"""
with open('.ai/README.md', 'w', encoding='utf-8') as f:
    f.write(ai_readme)
print('Created .ai/README.md')
