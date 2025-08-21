import { APIBaseInteraction, InteractionResponseType, MessageFlags } from 'discord-api-types/v10';
import { BaseCommand, Candidate, ServerMember } from '../types';
import { globalState } from '..';
import { api, PRESIDENTIAL_VOTE_TIME } from '../utils';
import { escapeMarkdown } from '@discordjs/formatters';

export default class extends BaseCommand {
	async handle(interaction: APIBaseInteraction<any, any>, sender: ServerMember): Promise<any> {
		const slogan = escapeMarkdown(interaction.data.options[0].value).replace(/[\n\r\t]/g, ''); // remove fancy line chars

		// Check if already campaigning
		for (const sample of globalState.serverData.candidates) {
			if (sample.getID() == sender.getID()) {
				return {
					type: InteractionResponseType.ChannelMessageWithSource,
					data: {
						flags: MessageFlags.Ephemeral,
						content: `You're already campaigning!`,
					},
				};
			}
		}

		if (globalState.serverData.presidentID == sender.getID()) {
			return {
				type: InteractionResponseType.ChannelMessageWithSource,
				data: {
					flags: MessageFlags.Ephemeral,
					content: globalState.isLastTerm()
						? 'You cannot be elected President for more than two terms at a time'
						: 'The President (you) automatically runs for re-election once voting begins',
				},
			};
		}

		if (globalState.isPresidentialVoteActive()) {
			const daysRemaining = globalState.millisRemainingInTerm() / 8.64e7;
			return {
				type: InteractionResponseType.ChannelMessageWithSource,
				data: {
					flags: MessageFlags.Ephemeral,
					content: `Polls for the **${globalState.ordinal(
						globalState.getPresidentialCount() + 1
					)} Presidential Election** open **${globalState.getUSTime(
						Date.now() + globalState.millisRemainingInTerm() - PRESIDENTIAL_VOTE_TIME
					)} EST**. The President has ${daysRemaining} day${daysRemaining != 1 ? 's' : ''} and ${
						(daysRemaining % 1) * 24
					} hours left in office. You will be notified in <#${globalState.serverData.votingBoothChannel}> when the election begins`,
				},
			};
		}

		if (globalState.serverData.candidates.length == 10) {
			return {
				type: InteractionResponseType.ChannelMessageWithSource,
				data: {
					flags: MessageFlags.Ephemeral,
					content: 'There are too many candidates running for office. Only 10 at a time!',
				},
			};
		}

		const maxSloganLength = 200;
		if (slogan.length > maxSloganLength) {
			return {
				type: InteractionResponseType.ChannelMessageWithSource,
				data: {
					flags: MessageFlags.Ephemeral,
					content: `Slogan must be less than ${maxSloganLength} characters`,
				},
			};
		}

		const party = sender.getPoliticalParty();

		if (!party) {
			return {
				type: InteractionResponseType.ChannelMessageWithSource,
				data: {
					flags: MessageFlags.Ephemeral,
					content: `You need to join a party first`,
				},
			};
		}

		// We're good now! Add the candidate
		globalState.serverData.candidates.push(new Candidate(sender.getID(), sender.getPartyID(), globalState.getNextCandidateSlot(), slogan));
		const urlPrefix = `channels/${globalState.serverData.votingBoothChannel}/messages/${globalState.serverData.presidentialVoteMessageID}/reactions/`;

		for (let i = 0; i < globalState.serverData.candidates.length; i++) {
			if (i != 9) {
				api(`${urlPrefix}${globalState.unicodeToEmoji(`U+3${i + 1}U+fe0fU+20e3`)}/@me`, {
					method: 'PUT',
				});
			} else {
				api(`${urlPrefix}${globalState.unicodeToEmoji(`U+1f51f`)}/@me`, {
					method: 'PUT',
				});
			}
		}

		await api(`channels/${globalState.serverData.votingBoothChannel}/messages`, {
			method: 'PATCH',
			body: await globalState.buildPresidentialVote(),
		});

		return {
			type: InteractionResponseType.ChannelMessageWithSource,
			data: {
				content: `You are now campaigning! Check the Presidential Voting poll in <#${globalState.serverData.votingBoothChannel}>`,
			},
		};
	}
}
