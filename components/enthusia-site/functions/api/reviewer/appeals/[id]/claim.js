import { json, methodNotAllowed, serviceUnavailable } from "../../../../lib/responses.js";
import { authenticatedReviewer } from "../../../../lib/reviewer-auth.js";
import { boundedIdempotencyKey, requireSameOrigin } from "../../../../lib/security.js";
import { signedStaffRequest, staffApiResponse } from "../../../../lib/staff-api.js";
import { isCanonicalUuid } from "../../../../lib/validation.js";

function sanitizeClaim(input) {
  const expectedVersion = Number(input?.expectedVersion);
  const idempotencyKey = boundedIdempotencyKey(input?.idempotencyKey);
  if (!Number.isSafeInteger(expectedVersion) || expectedVersion < 1 || !idempotencyKey) return null;
  return { expectedVersion, idempotencyKey };
}

async function parseClaimRequest(context, reviewer) {
  let claim;
  try {
    claim = sanitizeClaim(await context.request.json());
  } catch {
    claim = null;
  }
  const appealId = String(context.params.id ?? "").trim();
  if (!claim || !isCanonicalUuid(appealId)) {
    return { response: json({ error: "invalid_claim" }, 400) };
  }
  return { reviewer, claim, appealId };
}

async function sendClaim(context, request) {
  try {
    const upstream = await signedStaffRequest(
      context.env,
      `/v1/website/appeals/reviewer/${request.appealId}/claim`,
      {
        actorAccountId: request.reviewer.session.player.uuid,
        actorRank: request.reviewer.actorRank,
        expectedVersion: request.claim.expectedVersion,
        idempotencyKey: request.claim.idempotencyKey
      }
    );
    return staffApiResponse(upstream);
  } catch {
    return serviceUnavailable();
  }
}

export async function onRequestPost(context) {
  if (!requireSameOrigin(context.request)) return json({ error: "invalid_origin" }, 403);
  const reviewer = await authenticatedReviewer(context.request, context.env);
  if (reviewer.error) return reviewer.error;
  const request = await parseClaimRequest(context, reviewer);
  if (request.response) return request.response;
  return sendClaim(context, request);
}

export function onRequest() { return methodNotAllowed(["POST"]); }
export { sanitizeClaim };
