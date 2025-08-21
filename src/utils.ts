import { globalState } from '.';

export interface APIOptions {
	method: string;
	body?: any;
}

export async function api(endpoint: string, options: APIOptions = { method: 'GET' }): Promise<any> {
	// append endpoint to root API URL
	const url = 'https://discord.com/api/v10/' + endpoint;
	// Stringify payloads
	if (options.body) options.body = JSON.stringify(options.body);

	// Define a helper function for making the request and handling 429 retries
	const makeRequest = async (): Promise<any> => {
		const timeoutPromise = new Promise<any>((_, reject) => setTimeout(() => reject(new Error('Request timed out')), 5000));

		const res = await Promise.race([
			fetch(url, {
				headers: {
					Authorization: `Bot ${globalState.env.BOT_TOKEN}`,
					'Content-Type': 'application/json; charset=UTF-8',
				},
				...options,
			}),
			timeoutPromise,
		]);

		// If status is 429, handle the retry logic
		if (res.status === 429) {
			const retryAfter = res.headers.get('X-RateLimit-Reset-After') || '1'; // Default to 1 second if header is missing
			const globalRateLimit = res.headers.get('X-RateLimit-Global'); // Check if global rate limit
			const resetAfter = parseFloat(retryAfter) * 1000; // Convert to milliseconds

			// If it's a global rate limit, delay the retry
			if (globalRateLimit) {
				console.warn(`Global rate limit hit, retrying in ${resetAfter / 1000} seconds...`);
			} else {
				console.warn(`Rate limit hit, retrying in ${resetAfter / 1000} seconds...`);
			}

			// Wait for the reset time before retrying
			await new Promise((resolve) => setTimeout(resolve, resetAfter));

			// Retry the request after the delay
			return makeRequest();
		}

		// If the response is not OK, throw an error with the details
		if (!res.ok) {
			const data = await res.json();
			console.error(`Failed on ${url}: ${res.status}`);
			console.error(`Content:`, options);
			throw new Error(JSON.stringify(data));
		}

		// Don't try to parse JSON if it's just a 204 No Content
		if (res.status === 204) return null;

		// Return original response data if no issues
		return res.json();
	};

	// Make the request and return the result
	return makeRequest();
}

// BEGIN DBOT SPECIFIC HELPER FUNCTIONS
export const TERM_LENGTH = 2592000000;
export const PRESIDENTIAL_VOTE_TIME = 259200000;
export const CHECK_POLL_RESULT_TIME = 900000; // 15 mins
export const CAQ_UPDATE_TIME = 86400000;
export const AMENDMENT_CACHE_TIME = 86400000; // one day
export const MAX_POLLS = 10;
export const BAD_COLOR_RESPONSE = 'Unknown color name or invalid [hex color code](<https://rgbcolorcode.com>)';

// chatgpt dump of common colors
const COLOR_NAME_TO_HEX: Record<string, string> = {
	black: '#000000',
	white: '#FFFFFF',
	red: '#FF0000',
	lime: '#00FF00',
	blue: '#0000FF',
	yellow: '#FFFF00',
	cyan: '#00FFFF',
	aqua: '#00FFFF',
	magenta: '#FF00FF',
	fuchsia: '#FF00FF',
	silver: '#C0C0C0',
	gray: '#808080',
	grey: '#808080', // i hate britain
	maroon: '#800000',
	olive: '#808000',
	green: '#008000',
	purple: '#800080',
	teal: '#008080',
	navy: '#000080',
	orange: '#FFA500',
	pink: '#FFC0CB',
	brown: '#A52A2A',
	gold: '#FFD700',
	beige: '#F5F5DC',
	indigo: '#4B0082',
	violet: '#EE82EE',
	chocolate: '#D2691E',
	coral: '#FF7F50',
	crimson: '#DC143C',
	darkblue: '#00008B',
	darkcyan: '#008B8B',
	darkgray: '#A9A9A9',
	darkgreen: '#006400',
	darkmagenta: '#8B008B',
	darkred: '#8B0000',
	darkorange: '#FF8C00',
	lightblue: '#ADD8E6',
	lightgreen: '#90EE90',
	lightgray: '#D3D3D3',
	lightpink: '#FFB6C1',
	lavender: '#E6E6FA',
	salmon: '#FA8072',
	sienna: '#A0522D',
};

export function parseColor(raw: string) {
	console.log(raw);
	const lower = raw.toLowerCase();

	// Try common color names
	if (lower in COLOR_NAME_TO_HEX) {
		return hexToColorInt(COLOR_NAME_TO_HEX[lower]);
	} else {
		console.warn(`Failed to parse user's color as a known name: ${raw}`);
	}

	// Try to parse it as hex (use regex to check if it's good)
	const hex = `#${raw}`;
	if (/^#[0-9A-Fa-f]{6}$/.test(hex)) {
		return hexToColorInt(hex.toUpperCase());
	} else {
		console.warn(`Can't parse user's color as a hex code: ${raw}`);
	}

	return null;
}

function hexToColorInt(hex: string) {
	return parseInt(hex.substring(1), 16);
}

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
