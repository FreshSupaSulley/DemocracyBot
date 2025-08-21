import { escapeMarkdown } from '@discordjs/formatters';
import { globalState } from '..';
import BasePoll from './poll';
import { InteractionResponseType } from 'discord-interactions';

export default class ProposePoll extends BasePoll {
	proposal: string;

	constructor(proposal: string) {
		// Embed titles don't support markdown
		super('propose', 0.5, 5, 43200000);
		this.proposal = escapeMarkdown(proposal);
	}

	firePoll() {
		this.question = 'New amendment: ' + escapeMarkdown(this.proposal);
		return super.firePoll();
	}

	isDuplicate(sample: ProposePoll): boolean {
		return this.proposal == sample.proposal;
	}

	async pollPassed() {
		return globalState.addAmendment(this.proposal);
	}
}
