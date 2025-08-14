export class PoliticalParty {
	// Also serves as the party ID
	public readonly role: string;
	private readonly category: string;

	private blacklist: Set<string> = new Set();

	/** Leaders can change */
	public leader: string;

	constructor(role: string, category: string, owner: string) {
		this.role = role;
		this.category = category;
		this.leader = owner;
	}

	/**
	 * Checks if the member is banned.
	 *
	 * @param member member ID
	 * @returns true if the member is banned, false otherwise
	 */
	public isBanned(member: string): boolean {
		return this.blacklist.has(member);
	}

	/**
	 * Gets the ban list of the party.
	 *
	 * @returns list of all banned user IDs
	 */
	public getBlacklist(): Set<string> {
		return this.blacklist;
	}

	/**
	 * Bans a member from this party.
	 *
	 * @param member member ID
	 * @returns true if the member was banned, false if already banned
	 */
	public addBan(member: string): boolean {
		if (this.blacklist.has(member)) return false;
		this.blacklist.add(member);
		return true;
	}

	/**
	 * Unbans a member from this party.
	 *
	 * @param member member ID
	 * @returns true if the member was unbanned, false if was never banned
	 */
	public removeBan(member: string): boolean {
		return this.blacklist.delete(member);
	}

	public getRole(): string {
		return this.role;
	}

	public getCategory(): string {
		return this.category;
	}

	public equals(party: PoliticalParty | null): boolean {
		return party !== null && party.getRole() === this.role;
	}
}
