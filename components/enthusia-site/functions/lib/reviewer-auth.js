import { authenticateRequest, canReview } from "./auth.js";
import { forbidden, unauthorized } from "./responses.js";
import { reviewerRank } from "./staff-api.js";

export async function authenticatedReviewer(request, env) {
  let session;
  try {
    session = await authenticateRequest(request, env);
  } catch {
    return { error: unauthorized() };
  }
  if (!canReview(session, env)) return { error: forbidden() };
  const actorRank = reviewerRank(session);
  return actorRank ? { session, actorRank } : { error: forbidden() };
}
