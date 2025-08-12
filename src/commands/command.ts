import type { APIApplicationCommandSubcommandOption, APIApplicationCommandInteraction } from 'discord-api-types/v10';
import ServerData from '../types';
import { api } from '../utils';

export abstract class BaseCommand {
	constructor(data: APIApplicationCommandSubcommandOption, protected serverData: ServerData) {}

	isPresidentialVoteActive(): boolean {
		return !!this.serverData.presidentialVoteMessageID;
	}

	millisRemainingInTerm(): number {
		return this.serverData.termEndTime - Date.now();
	}

	getExactTime(time: number) {
		return new Date(time).toLocaleString('en-US', {
			month: '2-digit',
			day: '2-digit',
			year: 'numeric',
			hour: 'numeric',
			minute: '2-digit',
			hour12: true,
		});
	}

	abstract handle(interaction: APIApplicationCommandInteraction): Promise<any>;
}

// export interface Command {
// 	data: APIApplicationCommandSubcommandOption;
// 	handle: (interaction: APIApplicationCommandInteraction, data: ServerData) => Promise<any>;
// }

export function isPresidentialVoteActive(data: ServerData) {
	return !!data.presidentialVoteMessageID;
}

export function fetchPresidentialVote(env: any) {
	return env.DBOT.get('presidentialVoteMessageID').then((message: any) => {
		if (message) {
			return api(`channels/${env.DBOT.get('votingBooth')}/messages/${message}`);
		}
		return undefined;
	});
}

export function millisRemainingInTerm(data: ServerData): number {
	return Math.max(0, data.termEndTime - Date.now());
}
