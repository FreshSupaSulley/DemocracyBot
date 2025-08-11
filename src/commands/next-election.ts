import { APIApplicationCommandInteraction, InteractionResponseType } from 'discord-api-types/v10';
import { Command } from './command';
import { InteractionResponseFlags } from 'discord-interactions';
import { getExactTime, isPresidentialVoteActive, millisRemainingInTerm, PRESIDENTIAL_VOTE_TIME } from '../utils';

export const command: Command = {
	data: { name: 'next-election', options: [], description: 'Returns next election time', type: 1 },
	handle: async (interaction: APIApplicationCommandInteraction, data) => {
		return {
			type: InteractionResponseType.ChannelMessageWithSource,
			data: {
				content: isPresidentialVoteActive(data)
					? `The current election will end at **${getExactTime(Date.now() + (await millisRemainingInTerm(env)))} EST**.`
					: `The next election opens on **${getExactTime(Date.now() + millisRemainingInTerm(env) - PRESIDENTIAL_VOTE_TIME)} EST**.`,
			},
		};
	},
};

export default command;
