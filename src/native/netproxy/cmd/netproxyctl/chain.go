package main

import (
	"context"
	"errors"
	"fmt"
	"io"
	"net/http"
	"os"
	"strings"

	"github.com/Fanju6/NetProxy-Magisk/src/native/netproxy/internal/chain"
	"github.com/Fanju6/NetProxy-Magisk/src/native/netproxy/internal/contract"
	moduleapp "github.com/Fanju6/NetProxy-Magisk/src/native/netproxy/internal/module"
)

// chainImportLimit 限制一次导入的后置代理文件大小；一行一个代理，正常列表远小于这个值。
const chainImportLimit = 4 << 20

// chainCLIError 与 HTTP API 一致：输入校验错误转换为 chain.invalid，保留已有的结构化错误。
func chainCLIError(err error) error {
	if err == nil {
		return nil
	}
	if _, ok := errors.AsType[*contract.Error](err); ok {
		return err
	}
	if invalid, ok := errors.AsType[*chain.InvalidError](err); ok {
		return chainInvalid(invalid.Error())
	}
	return err
}

func chainInvalid(message string) error {
	return &contract.Error{Code: "chain.invalid", Message: message, HTTPStatus: http.StatusUnprocessableEntity}
}

// chain 管理链式代理：模式、后置代理、前置与按应用策略；行为与 /api/v1/chain 相同。
func (c *cli) chain(ctx context.Context, args []string) error {
	if len(args) == 0 {
		args = []string{"status"}
	}
	switch args[0] {
	case "status":
		if len(args) != 1 {
			return usageError("用法: netproxyctl chain status")
		}
		return c.chainStatus(ctx)
	case "mode":
		return c.chainMode(ctx, args[1:])
	case "landing":
		return c.chainLanding(ctx, args[1:])
	case "front":
		return c.chainFront(ctx, args[1:])
	case "policy":
		return c.chainPolicy(ctx, args[1:])
	default:
		return usageError("未知 chain 操作，使用 netproxyctl help 查看帮助")
	}
}

func (c *cli) chainStatus(ctx context.Context) error {
	data, err := moduleapp.ReadChain(ctx, c.options)
	if err != nil {
		return err
	}
	writeJSON(os.Stdout, result{Schema: 1, OK: true, Code: "chain.current", Message: "链式代理设置", Data: data})
	return nil
}

// updateChain 修改链式代理状态并输出新的完整设置；服务运行时由模块负责切换或重载核心。
func (c *cli) updateChain(ctx context.Context, code, message string, change func(*chain.State) error) error {
	data, err := moduleapp.UpdateChain(ctx, c.options, change)
	if err != nil {
		return chainCLIError(err)
	}
	writeJSON(os.Stdout, result{Schema: 1, OK: true, Code: code, Message: message, Data: data})
	return nil
}

// chainMode 切换后置模式；省略后置时保留当前的手动后置，与 Android 管理器一致。
func (c *cli) chainMode(ctx context.Context, args []string) error {
	if len(args) == 0 || len(args) > 2 {
		return usageError("用法: netproxyctl chain mode <off|manual|policy|manual_policy> [后置]")
	}
	return c.updateChain(ctx, "chain.saved", "链式代理设置已保存", func(state *chain.State) error {
		state.Mode = args[0]
		if len(args) == 2 {
			state.Manual = args[1]
		}
		return nil
	})
}

// chainFront 指定链式前置：follow 跟随节点页，<分组> 使用分组的自动选择，<分组>/<节点> 固定节点，smart <ID> 使用智能分组。
func (c *cli) chainFront(ctx context.Context, args []string) error {
	var front *chain.Front
	switch {
	case len(args) == 1 && args[0] == "follow":
	case len(args) == 2 && args[0] == "smart" && args[1] != "":
		front = &chain.Front{SmartID: args[1]}
	case len(args) == 1 && args[0] != "" && args[0] != "smart":
		if group, node, ok := splitReference(args[0]); ok {
			front = &chain.Front{GroupID: group, Node: node}
		} else if strings.Contains(args[0], "/") {
			return usageError("节点引用格式为 <分组>/<节点>")
		} else {
			front = &chain.Front{GroupID: args[0]}
		}
	default:
		return usageError("用法: netproxyctl chain front <follow|<分组>|<分组>/<节点>|smart <智能分组ID>>")
	}
	if front != nil {
		if err := moduleapp.CheckChainFront(ctx, c.options, *front); err != nil {
			return chainInvalid("指定的前置不可用: " + err.Error())
		}
	}
	return c.updateChain(ctx, "chain.front_saved", "前置已保存", func(state *chain.State) error {
		state.Front = front
		return nil
	})
}

func (c *cli) chainLanding(ctx context.Context, args []string) error {
	if len(args) == 0 {
		args = []string{"list"}
	}
	action, rest := args[0], args[1:]
	switch action {
	case "list":
		if len(rest) != 0 {
			return usageError("用法: netproxyctl chain landing list")
		}
		return c.chainStatus(ctx)
	case "import":
		return c.chainImport(ctx, rest)
	case "use":
		if len(rest) != 1 {
			return usageError("用法: netproxyctl chain landing use <后置>")
		}
		return c.updateChain(ctx, "chain.saved", "链式代理设置已保存", func(state *chain.State) error {
			state.Manual = rest[0]
			return nil
		})
	case "rename":
		if len(rest) != 2 {
			return usageError("用法: netproxyctl chain landing rename <后置> <新名称>")
		}
		return c.updateChain(ctx, "chain.landing_renamed", "后置代理已重命名", func(state *chain.State) error {
			return state.RenameLanding(rest[0], rest[1])
		})
	case "remove":
		if len(rest) != 1 {
			return usageError("用法: netproxyctl chain landing remove <后置>")
		}
		return c.updateChain(ctx, "chain.landing_removed", "后置代理已删除", func(state *chain.State) error {
			return state.RemoveLanding(rest[0])
		})
	case "test":
		if len(rest) > 1 {
			return usageError("用法: netproxyctl chain landing test [后置]")
		}
		data, err := moduleapp.TestChainLandings(ctx, c.options, first(rest))
		if err != nil {
			return err
		}
		writeJSON(os.Stdout, result{Schema: 1, OK: true, Code: "chain.delay", Message: "后置代理测速完成", Data: data})
		return nil
	default:
		return usageError("未知 chain landing 操作，使用 netproxyctl help 查看帮助")
	}
}

// chainImport 从文件导入后置代理；--type 指定无协议头的行按 socks 还是 http 解析，可写在文件前后。
func (c *cli) chainImport(ctx context.Context, args []string) error {
	kind := "socks"
	var files []string
	for i := 0; i < len(args); i++ {
		switch arg := args[i]; {
		case arg == "--type" && i+1 < len(args):
			kind = args[i+1]
			i++
		case strings.HasPrefix(arg, "--type="):
			kind = strings.TrimPrefix(arg, "--type=")
		case strings.HasPrefix(arg, "-"):
			return usageError("未知选项 " + arg)
		default:
			files = append(files, arg)
		}
	}
	if len(files) != 1 || (kind != "socks" && kind != "http") {
		return usageError("用法: netproxyctl chain landing import <文件> [--type socks|http]")
	}
	content, err := readChainImport(files[0])
	if err != nil {
		return err
	}
	if strings.TrimSpace(content) == "" {
		return chainInvalid("导入内容不能为空")
	}
	document, diagnostics, err := chain.ParseLandings(ctx, content, kind)
	if err != nil {
		return chainInvalid(err.Error())
	}
	var imported chain.ImportResult
	view, err := moduleapp.UpdateChain(ctx, c.options, func(state *chain.State) error {
		imported = state.AppendLandings(document)
		return nil
	})
	if err != nil {
		return chainCLIError(err)
	}
	imported.Diagnostics = diagnostics
	writeJSON(os.Stdout, result{Schema: 1, OK: true, Code: "chain.landings_imported", Message: "后置代理已导入",
		Data: map[string]any{"chain": view, "import": imported}})
	return nil
}

func readChainImport(path string) (string, error) {
	file, err := os.Open(path)
	if err != nil {
		return "", chainInvalid("无法读取导入文件: " + err.Error())
	}
	defer file.Close()
	content, err := io.ReadAll(io.LimitReader(file, chainImportLimit+1))
	if err != nil {
		return "", chainInvalid("无法读取导入文件: " + err.Error())
	}
	if len(content) > chainImportLimit {
		return "", chainInvalid(fmt.Sprintf("导入文件超过 %d MiB", chainImportLimit>>20))
	}
	return string(content), nil
}

// chainPolicy 查看策略组、启用或停用某个组，以及调整组的顺序（顺序决定匹配优先级）。
// 新建与编辑策略组的应用、出口等细节请使用 Android 管理器或 HTTP API。
func (c *cli) chainPolicy(ctx context.Context, args []string) error {
	usage := "用法: netproxyctl chain policy list|enable <ID>|disable <ID>|order <ID>..."
	if len(args) == 0 || args[0] == "list" {
		if len(args) > 1 {
			return usageError(usage)
		}
		return c.chainStatus(ctx)
	}
	switch args[0] {
	case "enable", "disable":
		if len(args) != 2 {
			return usageError(usage)
		}
		enabled := args[0] == "enable"
		return c.updateChain(ctx, "chain.policy_saved", "策略组已保存", func(state *chain.State) error {
			index := state.FindPolicy(args[1])
			if index < 0 {
				return &contract.Error{Code: "chain.policy_not_found", Message: "策略组不存在", HTTPStatus: http.StatusNotFound}
			}
			state.Policies[index].Enabled = enabled
			return nil
		})
	case "order":
		if len(args) < 2 {
			return usageError(usage)
		}
		return c.updateChain(ctx, "chain.policies_ordered", "策略组顺序已保存", func(state *chain.State) error {
			return state.ReorderPolicies(args[1:])
		})
	default:
		return usageError(usage)
	}
}
