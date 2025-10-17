import { escapeMarkdown } from '@discordjs/formatters';
import { globalState } from '..';
import BasePoll from './poll';

export default class ProposePoll extends BasePoll {
	proposal: string;

	constructor(proposal: string) {
		// Embed titles don't support markdown
		super('propose', 0.5, 3, 43200000);
		this.proposal = proposal;
	}

	firePoll() {
		this.question = 'New amendment: ' + escapeMarkdown(this.proposal).replace(/<@\d+/g, '[A MENTIONED PERSON]');
		return super.firePoll();
	}

	isDuplicate(sample: ProposePoll): boolean {
		return this.proposal == sample.proposal;
	}

	async pollPassed() {
		return globalState.addAmendment(this.proposal);
	}
}
