from pathlib import Path
import sys

source = Path(sys.argv[1])
target = Path(sys.argv[2])
text = source.read_text(encoding="utf-8")
block = '''replace_once(evidence_store,
             "            UUID caseId,\\n            ModerationSubjectId subjectId",
             "            CaseId caseId,\\n            ModerationSubjectId subjectId")
'''
if text.count(block) != 1:
    raise SystemExit(f"expected one evidence cardinality block, found {text.count(block)}")
target.write_text(text.replace(block, "", 1), encoding="utf-8")
