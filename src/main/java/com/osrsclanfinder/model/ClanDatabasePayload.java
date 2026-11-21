package com.osrsclanfinder.model;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClanDatabasePayload
{
	private String schemaVersion;
	private String databaseUpdatedDate;
	private String dataIntegrityPolicy;
	private List<ClanEntry> clans;
}
