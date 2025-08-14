import { globalState } from ".";

export interface APIOptions {
	method: string;
	body?: any;
}

export async function api(endpoint: string, options: APIOptions = { method: 'GET' }) {
	// append endpoint to root API URL
	const url = 'https://discord.com/api/v10/' + endpoint;
	// Stringify payloads
	if (options.body) options.body = JSON.stringify(options.body);
	// Use fetch to make requests
	const res = await fetch(url, {
		headers: {
			Authorization: `Bot ${globalState.env.BOT_TOKEN}`,
			'Content-Type': 'application/json; charset=UTF-8',
		},
		...options,
	});
	// throw API errors
	if (!res.ok) {
		const data = await res.json();
		console.log(res.status);
		throw new Error(JSON.stringify(data));
	}
	// return original response
	return res.json();
}

// BEGIN DBOT SPECIFIC HELPER FUNCTIONS
export const TERM_LENGTH = 2592000000;
export const PRESIDENTIAL_VOTE_TIME = 259200000;
export const CAQ_UPDATE_TIME = 86400000;
export const AMENDMENT_CACHE_TIME = 86400000; // one day

export function isPresidentialVoteActive(data: any) {
	return !!data.presidentialVoteMessageID;
}

// export function fetchPresidentialVote(env: any) {
// 	return env.DBOT.get('presidentialVoteMessageID').then((message: any) => {
// 		if (message) {
// 			return api(`channels/${env.DBOT.get('votingBooth')}/messages/${message}`);
// 		}
// 		return undefined;
// 	});
// }
