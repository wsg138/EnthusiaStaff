import { methodNotAllowed, serviceUnavailable } from "../../lib/responses.js";
import { authenticatedReviewer } from "../../lib/reviewer-auth.js";
import { signedStaffRequest, staffApiResponse } from "../../lib/staff-api.js";

export async function onRequestGet(context) {
  const reviewer = await authenticatedReviewer(context.request, context.env);
  if (reviewer.error) return reviewer.error;
  const { session, actorRank } = reviewer;

  const url = new URL(context.request.url);
  const status = url.searchParams.get("status")?.slice(0, 32) || "OPEN";
  const cursor = url.searchParams.get("cursor")?.slice(0, 128) || null;

  try {
    const upstream = await signedStaffRequest(context.env, "/v1/website/appeals/reviewer/list", {
      actorAccountId: session.player.uuid,
      actorRank,
      status,
      cursor,
      limit: 50,
    });
    return staffApiResponse(upstream);
  } catch {
    return serviceUnavailable();
  }
}

export function onRequest() { return methodNotAllowed(["GET"]); }
