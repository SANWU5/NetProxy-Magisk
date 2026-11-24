package smartgroup

import (
	"errors"
	"testing"
	"time"
)

func TestSourceSelectionFiltersAndSurvivesGenerate(t *testing.T) {
	sources := []Source{
		{GroupID: "sub-a", Tags: []string{"🇭🇰 香港 01", "🇯🇵 日本 01"}},
		{GroupID: "sub-b", Tags: []string{"🇺🇸 美国 01"}},
	}
	if got := (SourceSelection{}).Filter(sources); len(got) != 2 {
		t.Fatalf("未设置范围应使用全部来源: %v", got)
	}
	selection := SourceSelection{Mode: SourcesCustom, Groups: []string{"sub-b"}}
	if got := selection.Filter(sources); len(got) != 1 || got[0].GroupID != "sub-b" {
		t.Fatalf("自选范围只应保留所选分组: %v", got)
	}
	doc, _ := Generate(Document{Sources: selection}, selection.Filter(sources), false, time.Now())
	if doc.Sources.Mode != SourcesCustom || len(doc.Sources.Groups) != 1 {
		t.Fatalf("重新分组后应保留订阅分组范围: %+v", doc.Sources)
	}
	for _, group := range doc.Groups {
		for _, member := range group.Effective() {
			if member.GroupID != "sub-b" {
				t.Fatalf("自选范围外的节点不应进入地区分组: %+v", member)
			}
		}
	}
}

func TestSourceSelectionValidation(t *testing.T) {
	for name, selection := range map[string]SourceSelection{
		"自选为空":  {Mode: SourcesCustom},
		"未知范围":  {Mode: "some"},
		"重复分组":  {Mode: SourcesCustom, Groups: []string{"a", "a"}},
		"空分组 ID": {Mode: SourcesCustom, Groups: []string{" "}},
	} {
		var invalid *InvalidError
		if err := (Document{Sources: selection}).Validate(); !errors.As(err, &invalid) {
			t.Fatalf("%s 应被拒绝: %v", name, err)
		}
	}
	if err := (Document{Sources: SourceSelection{Mode: SourcesAll}}).Validate(); err != nil {
		t.Fatalf("全部范围应有效: %v", err)
	}
}
