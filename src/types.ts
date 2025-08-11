export default interface ServerData {
	serverID: string;
	// Channels
	github: string;
	constipation: string;
	amendments: string;
	commandersAndQueefs: string;
	votingBooth: string;
	testChannel: string;
	// Webhook for updating
	githubWebhookID: string;
	// Roles
	thePresident: string;
	citizen: string;

	presidentialCount: number;
	amendmentIDs: [string];
	caqEntries: { [messageID: string]: string };
	polls: [Poll];
	pollCooldownExpiryTimes: {
		[userID: string]: {
			[poll: string]: number;
		};
	};
	members: [
		{
			[userID: number]: number;
			partyRole: number;
		}
	];
	parties: {
		[party: string]: {
			role: number;
			category: number;
			blacklist: [number];
			leader: number;
		};
	};
	naturalizedCitizens: [number];
	naturalizationBlacklist: [number];
	presidentID: number;
	slogan: string;
	termEndTime: number;
	lastTerm: boolean;
	presidentialVoteMessageID: number;
	candidates: [
		{
			slot: number;
			slogan: string;
			userID: number;
			partyRole: number;
		}
	];
}

// Polls
export const POLL_QUESTION_PREFIX = 50;
export const YES_EMOJI = 'U+2705';
export const NO_EMOJI = 'U+1f6ab';

export interface Poll {
	question: string;
	messageID: number;
	startTime: number;
	ratio: number;
	minParticipation: number;
	votingCooldown: number;
	pollPassed: () => void;
	pollFailed: () => void;
}
