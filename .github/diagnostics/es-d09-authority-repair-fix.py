from pathlib import Path
import sys

source = Path(sys.argv[1])
target = Path(sys.argv[2])
text = source.read_text(encoding="utf-8")
blocks = [
    '''replace_once(evidence_store,
             "            UUID caseId,\\n            ModerationSubjectId subjectId",
             "            CaseId caseId,\\n            ModerationSubjectId subjectId")
''',
    '''replace_once(evidence_store,
             "                       m.captured_at, e.investigation_case_id, e.last_observed_at, e.revision",
             "                       m.captured_at, e.case_id, e.last_observed_at, e.revision")
''',
    '''replace_once(worker,
             "                    observation.issuerId(),\\n                    observation.summary(),",
             "                    observation.issuer(),\\n                    observation.consequenceType(),\\n                    observation.summary(),")
''',
]
for block in blocks:
    count = text.count(block)
    if count != 1:
        raise SystemExit(f"expected one diagnostic replacement block, found {count}: {block[:80]!r}")
    text = text.replace(block, "", 1)
text += '''\n# Source indentation differs from the draft transform; apply the worker mapping explicitly.\nworker = "staff-bot/src/main/java/net/enthusia/staff/discordbot/DiscordInvestigationWorker.java"\ncontent = read(worker)\nold = "                observation.issuerId(),\\n                observation.summary(),"\nnew = "                observation.issuer(),\\n                observation.consequenceType(),\\n                observation.summary(),"\nif content.count(old) != 1:\n    raise RuntimeError(f"worker provenance mapping expected once, found {content.count(old)}")\nwrite(worker, content.replace(old, new, 1))\n'''
target.write_text(text, encoding="utf-8")
