import { HonAuthError, type FetchFunction, type HonSession } from './auth/honSession';
import { HON_API_BASE_URL } from './honConstants';
import { WasherCommandError } from '../washer/washerTypes';

export interface HonApiRequest {
  method: 'GET' | 'POST';
  /** Path under the API, e.g. "/commands/v1/context". */
  path: string;
  query?: Record<string, string>;
  body?: unknown;
}

function isAuthRejection(status: number): boolean {
  return status === 401 || status === 403;
}

/**
 * Sends one request to the h.on API with the session's tokens. If h.on says
 * the tokens are no longer valid, it refreshes them and tries exactly once
 * more.
 */
export async function sendHonApiRequest<ResponseBody>(
  session: HonSession,
  fetchFunction: FetchFunction,
  request: HonApiRequest,
): Promise<ResponseBody> {
  const queryString = request.query
    ? '?' +
      Object.entries(request.query)
        .map(([key, value]) => `${key}=${encodeURIComponent(value)}`)
        .join('&')
    : '';
  const url = `${HON_API_BASE_URL}${request.path}${queryString}`;

  const send = async (): Promise<Response> =>
    fetchFunction(url, {
      method: request.method,
      headers: await session.authorizedHeaders(),
      body: request.body === undefined ? undefined : JSON.stringify(request.body),
    });

  let response = await send();
  if (isAuthRejection(response.status)) {
    await session.refreshTokens();
    response = await send();
    if (isAuthRejection(response.status)) {
      throw new HonAuthError('h.on no longer accepts your sign-in. Please sign in again.');
    }
  }
  if (!response.ok) {
    throw new WasherCommandError(`h.on returned an error (${response.status}). Try again in a minute.`);
  }
  return (await response.json()) as ResponseBody;
}
