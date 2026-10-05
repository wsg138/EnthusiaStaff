import { json, methodNotAllowed, serviceUnavailable } from "../../../../lib/responses.js";
import { authenticatedReviewer } from "../../../../lib/reviewer-auth.js";
import { boundedIdempotencyKey, requireSameOrigin } from "../../../../lib/security.js";
import { signedStaffRequest, staffApiResponse } from "../../../../lib/staff-api.js";
import { isCanonicalUuid } from "../../../../lib/validation.js";

function sanitizeReopen(input) {
  const expectedVersion = Number(input?.expectedVersion);
  const note = typeof input?.note === "string" ? input.note.trim() : "";
  const idempotencyKey = boundedIdempotencyKey(input?.idempotencyKey);
  if (!Number.isSafeInteger(expectedVersion) || expectedVersion < 1) return null;
  if (note.length < 3 || note.length > 1000 || !idempotencyKey) return null;
  return { expectedVersion, note, idempotencyKey };
}

export async function onRequestPost(context) {
  if (!requireSameOrigin(context.request)) return json({ error: "invalid_origin" }, 403);
  const reviewer = await authenticatedReviewer(context.request, context.env);
  if (reviewer.error) return reviewer.error;
  let reopen;
  try {
    reopen = sanitizeReopen(await context.request.json());
  } catch {
    reopen = null;
  }
  const appealId = String(context.params.id ?? "").trim();
  if (!reopen || !isCanonicalUuid(appealId)) return json({ error: "invalid_reopen" }, 400);

  try {
    const upstream = await signedStaffRequest(
      context.env,
      `/v1/website/appeals/reviewer/${appealId}/reopen`,
      {
        actorAccountId: reviewer.session.player.uuid,
        actorRank: reviewer.actorRank,
        expectedVersion: reopen.expectedVersion,
        note: reopen.note,
        idempotencyKey: reopen.idempotencyKey
      }
    );
    return staffApiResponse(upstream);
  } catch {
    return serviceUnavailable();
  }
}

export function onRequest() { return methodNotAllowed(["POST"]); }
export { sanitizeReopen };
