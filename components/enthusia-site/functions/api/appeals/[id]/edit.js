import { authenticateRequest } from "../../../lib/auth.js";
import { json, methodNotAllowed, serviceUnavailable, unauthorized } from "../../../lib/responses.js";
import { boundedIdempotencyKey, requireSameOrigin } from "../../../lib/security.js";
import { signedStaffRequest, staffApiResponse } from "../../../lib/staff-api.js";
import { isCanonicalUuid } from "../../../lib/validation.js";

function sanitizeEdit(input) {
  const expectedVersion = Number(input?.expectedVersion);
  const reason = typeof input?.reason === "string" ? input.reason.trim() : "";
  const idempotencyKey = boundedIdempotencyKey(input?.idempotencyKey);
  if (!Number.isSafeInteger(expectedVersion) || expectedVersion < 1) return null;
  if (reason.length < 10 || reason.length > 1000) return null;
  if (!idempotencyKey) return null;
  return { expectedVersion, reason, idempotencyKey };
}

export async function onRequestPost(context) {
  if (!requireSameOrigin(context.request)) return json({ error: "invalid_origin" }, 403);
  let session;
  try {
    session = await authenticateRequest(context.request, context.env);
  } catch {
    return unauthorized();
  }
  let edit;
  try {
    edit = sanitizeEdit(await context.request.json());
  } catch {
    edit = null;
  }
  const appealId = String(context.params.id ?? "").trim();
  if (!edit || !isCanonicalUuid(appealId)) return json({ error: "invalid_appeal_edit" }, 400);

  try {
    const upstream = await signedStaffRequest(
      context.env,
      `/v1/website/appeals/${appealId}/edit`,
      {
        accountId: session.player.uuid,
        expectedVersion: edit.expectedVersion,
        reason: edit.reason,
        idempotencyKey: edit.idempotencyKey
      }
    );
    return staffApiResponse(upstream, "private, no-store");
  } catch {
    return serviceUnavailable();
  }
}

export function onRequest() { return methodNotAllowed(["POST"]); }
export { sanitizeEdit };
