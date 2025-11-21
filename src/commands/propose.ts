import { APIBaseInteraction } from 'discord-api-types/v10';
import { globalState } from '..';
import ProposePoll from '../polls/propose-poll';
import { BaseCommand } from '../types';

export default class extends BaseCommand {
	async handle(interaction: APIBaseInteraction<any, any>): Promise<any> {
		return globalState.beginPoll(interaction, new ProposePoll(interaction.data.options[0].value));
	}
}
