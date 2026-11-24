package main

import (
	"context"
	"errors"
	"net/http"
	"os"

	"github.com/Fanju6/NetProxy-Magisk/src/native/netproxy/internal/contract"
	moduleapp "github.com/Fanju6/NetProxy-Magisk/src/native/netproxy/internal/module"
	"github.com/Fanju6/NetProxy-Magisk/src/native/netproxy/internal/smartgroup"
)

// smartCLIError 与 HTTP API 一致：分组不存在为 smart.not_found，输入校验错误为 smart.invalid。
func smartCLIError(err error) error {
	if err == nil {
		return nil
	}
	if errors.Is(err, moduleapp.ErrSmartGroupNotFound) {
		return &contract.Error{Code: "smart.not_found", Message: "分组不存在", HTTPStatus: http.StatusNotFound}
	}
	if _, ok := errors.AsType[*contract.Error](err); ok {
		return err
	}
	if invalid, ok := errors.AsType[*smartgroup.InvalidError](err); ok {
		return &contract.Error{Code: "smart.invalid", Message: invalid.Error(), HTTPStatus: http.StatusUnprocessableEntity}
	}
	return err
}

// smart 管理智能分组与自建分组：列出、重新分组、使用分组、组内选择与测速；行为与 /api/v1/smart-groups 相同。
func (c *cli) smart(ctx context.Context, args []string) error {
	if len(args) == 0 {
		args = []string{"list"}
	}
	action, rest := args[0], args[1:]
	var (
		code, message string
		data          any
		err           error
	)
	switch {
	case action == "list" && len(rest) == 0:
		code, message = "smart.list", "智能分组"
		data, err = moduleapp.ReadSmartGroups(ctx, c.options)
	case action == "generate" && len(rest) <= 1 && (len(rest) == 0 || rest[0] == "--english"):
		code, message = "smart.generated", "智能分组已完成"
		view, summary, generateErr := moduleapp.GenerateSmartGroups(ctx, c.options, len(rest) == 1)
		data, err = map[string]any{"smart": view, "summary": summary}, generateErr
	case action == "use" && len(rest) == 1:
		code, message = "node.selected", "节点选择已更新"
		data, err = moduleapp.SelectSmartGroup(ctx, c.options, rest[0])
	case action == "select" && len(rest) == 2:
		var manual *smartgroup.NodeRef
		if rest[1] != "auto" {
			group, node, ok := splitReference(rest[1])
			if !ok {
				return usageError("节点引用格式为 <分组>/<节点>，或 auto 改回自动选择")
			}
			manual = &smartgroup.NodeRef{GroupID: group, Node: node}
		}
		code, message = "node.selected", "节点选择已更新"
		data, err = moduleapp.SelectSmartNode(ctx, c.options, rest[0], manual)
	case action == "sources" && len(rest) == 0:
		code, message = "smart.list", "智能分组"
		data, err = moduleapp.ReadSmartGroups(ctx, c.options)
	case action == "sources":
		// sources all 使用全部订阅与本地分组；sources <分组ID>... 只使用这些分组，并立即重新分组。
		mode, groups := smartgroup.SourcesCustom, rest
		if len(rest) == 1 && rest[0] == "all" {
			mode, groups = smartgroup.SourcesAll, nil
		}
		code, message = "smart.sources_saved", "订阅分组范围已保存"
		view, summary, sourcesErr := moduleapp.SetSmartSources(ctx, c.options, mode, groups, false)
		data, err = map[string]any{"smart": view, "summary": summary}, sourcesErr
	case action == "test" && len(rest) <= 1:
		code, message = "smart.delay", "分组测速完成"
		data, err = moduleapp.TestSmartGroups(ctx, c.options, first(rest))
	default:
		return usageError("用法: netproxyctl smart <list|generate [--english]|use <ID>|select <ID> <分组>/<节点>|auto|test [ID]|sources [all|<分组ID>...]>")
	}
	if err != nil {
		return smartCLIError(err)
	}
	writeJSON(os.Stdout, result{Schema: 1, OK: true, Code: code, Message: message, Data: data})
	return nil
}
