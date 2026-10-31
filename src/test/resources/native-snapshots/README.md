# Historical server-table snapshot

This is the unchanged 5.3 MB DropTableExcelConfigData.json previously checked
into data/Server/. Two Java regression tests use it to verify the historical
graph and configured world-Boss rewards without requiring an external resource
pack in CI.

The table was inherited through the squashed Chiori import. Its original-game
provenance remains unverified, so passing tests show consistency with this
snapshot only. Runtime loads Server/DropTableExcelConfigData.json from the
external resources/ pack, and does not use this test fixture.