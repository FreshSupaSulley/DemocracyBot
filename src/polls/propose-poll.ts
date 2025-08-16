import { escapeMarkdown } from '@discordjs/formatters';
import { globalState } from '..';
import BasePoll from './poll';

export default class ProposePoll extends BasePoll {
	proposal: string;

	constructor(proposal: string) {
		// Embed titles don't support markdown
		// so we'll show the markdown here but it will be escaped when added
		super(0.5, 5, 43200000, 'New amendment: ' + proposal);
		this.proposal = proposal;
	}

	isDuplicate(sample: ProposePoll): boolean {
		return this.proposal == sample.proposal;
	}

	async pollPassed() {
		globalState.addAmendment(escapeMarkdown(this.proposal));
	}
}
