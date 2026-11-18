package catalog

import "testing"

func TestRegionKeyRecognizesCommonMarkers(t *testing.T) {
	for tag, want := range map[string]string{
		"🇯🇵日本东京A1": "jp", "香港 IPLC 01": "hk", "US-Seattle": "us", "未知节点": "other",
	} {
		if got := RegionKey(tag); got != want {
			t.Fatalf("RegionKey(%q) = %q, want %q", tag, got, want)
		}
	}
}
