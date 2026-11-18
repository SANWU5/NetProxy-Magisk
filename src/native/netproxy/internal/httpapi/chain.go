package httpapi

import (
	"context"
	"errors"
	"net/http"
	"slices"
	"strings"

	"github.com/Fanju6/NetProxy-Magisk/src/native/netproxy/internal/chain"
	"github.com/Fanju6/NetProxy-Magisk/src/native/netproxy/internal/contract"
	"github.com/Fanju6/NetProxy-Magisk/src/native/netproxy/internal/module"
	"github.com/gofrs/uuid/v5"
)

// chainError 把输入校验错误转换为 422，保留处理器自身返回的结构化错误（例如策略不存在）。
func chainError(err error) error {
	if err == nil {
		return nil
	}
	if _, ok := errors.AsType[*contract.Error](err); ok {
		return err
	}
	if invalid, ok := errors.AsType[*chain.InvalidError](err); ok {
		return &contract.Error{Code: "chain.invalid", Message: invalid.Error(), HTTPStatus: http.StatusUnprocessableEntity}
	}
	return err
}

func (s *Server) readChain(ctx context.Context, r *http.Request) (contract.Result, error) {
	if _, err := resourceQuery(r); err != nil {
		return contract.Result{}, err
	}
	data, err := module.ReadChain(ctx, s.options)
	return contract.Success("chain.current", "链式代理设置", data), err
}

func (s *Server) saveChain(ctx context.Context, r *http.Request) (contract.Result, error) {
	var input struct {
		Mode   string `json:"mode"`
		Manual string `json:"manual"`
	}
	if err := resourceDecode(r, &input); err != nil {
		return contract.Result{}, err
	}
	data, err := module.UpdateChain(ctx, s.options, func(state *chain.State) error {
		state.Mode, state.Manual = input.Mode, input.Manual
		return nil
	})
	return contract.Success("chain.saved", "链式代理设置已保存", data), chainError(err)
}

func (s *Server) importChainLandings(ctx context.Context, r *http.Request) (contract.Result, error) {
	var input struct {
		Content string `json:"content"`
		Type    string `json:"type"`
	}
	if err := resourceDecode(r, &input); err != nil {
		return contract.Result{}, err
	}
	if strings.TrimSpace(input.Content) == "" {
		return contract.Result{}, invalid("导入内容不能为空")
	}
	document, diagnostics, err := chain.ParseLandings(ctx, input.Content, input.Type)
	if err != nil {
		return contract.Result{}, invalid(err.Error())
	}
	var result chain.ImportResult
	view, err := module.UpdateChain(ctx, s.options, func(state *chain.State) error {
		result = state.AppendLandings(document)
		return nil
	})
	result.Diagnostics = diagnostics
	return contract.Success("chain.landings_imported", "后置代理已导入", map[string]any{"chain": view, "import": result}), chainError(err)
}

func (s *Server) renameChainLanding(ctx context.Context, r *http.Request) (contract.Result, error) {
	var input struct {
		Tag  string `json:"tag"`
		Name string `json:"name"`
	}
	if err := resourceDecode(r, &input); err != nil {
		return contract.Result{}, err
	}
	data, err := module.UpdateChain(ctx, s.options, func(state *chain.State) error {
		return state.RenameLanding(input.Tag, input.Name)
	})
	return contract.Success("chain.landing_renamed", "后置代理已重命名", data), chainError(err)
}

func (s *Server) removeChainLanding(ctx context.Context, r *http.Request) (contract.Result, error) {
	if err := resourceEmptyBody(r); err != nil {
		return contract.Result{}, err
	}
	query, err := resourceQuery(r, "tag")
	if err != nil {
		return contract.Result{}, err
	}
	tag := query.Get("tag")
	if tag == "" {
		return contract.Result{}, invalid("缺少后置代理名称")
	}
	data, err := module.UpdateChain(ctx, s.options, func(state *chain.State) error {
		return state.RemoveLanding(tag)
	})
	return contract.Success("chain.landing_removed", "后置代理已删除", data), chainError(err)
}

func (s *Server) testChainLandings(ctx context.Context, r *http.Request) (contract.Result, error) {
	var input struct {
		Tag string `json:"tag"`
	}
	if err := resourceDecode(r, &input); err != nil {
		return contract.Result{}, err
	}
	data, err := module.TestChainLandings(ctx, s.options, input.Tag)
	return contract.Success("chain.delay", "后置代理测速完成", data), err
}

type chainPolicyInput struct {
	Name     string   `json:"name"`
	Enabled  bool     `json:"enabled"`
	Landing  string   `json:"landing"`
	Packages []string `json:"packages"`
}

func (input chainPolicyInput) policy(id string) chain.Policy {
	packages := make([]string, 0, len(input.Packages))
	for _, name := range input.Packages {
		if name = strings.TrimSpace(name); name != "" && !slices.Contains(packages, name) {
			packages = append(packages, name)
		}
	}
	return chain.Policy{ID: id, Name: strings.TrimSpace(input.Name), Enabled: input.Enabled, Landing: input.Landing, Packages: packages}
}

func (s *Server) addChainPolicy(ctx context.Context, r *http.Request) (contract.Result, error) {
	var input chainPolicyInput
	if err := resourceDecode(r, &input); err != nil {
		return contract.Result{}, err
	}
	policy := input.policy(uuid.Must(uuid.NewV4()).String())
	data, err := module.UpdateChain(ctx, s.options, func(state *chain.State) error {
		state.Policies = append(state.Policies, policy)
		return nil
	})
	return contract.Success("chain.policy_created", "策略已创建", map[string]any{"chain": data, "policy": policy}), chainError(err)
}

func (s *Server) saveChainPolicy(ctx context.Context, r *http.Request) (contract.Result, error) {
	var input chainPolicyInput
	if err := resourceDecode(r, &input); err != nil {
		return contract.Result{}, err
	}
	id := r.PathValue("id")
	data, err := module.UpdateChain(ctx, s.options, func(state *chain.State) error {
		index := state.FindPolicy(id)
		if index < 0 {
			return &contract.Error{Code: "chain.policy_not_found", Message: "策略不存在", HTTPStatus: http.StatusNotFound}
		}
		state.Policies[index] = input.policy(id)
		return nil
	})
	return contract.Success("chain.policy_saved", "策略已保存", data), chainError(err)
}

func (s *Server) deleteChainPolicy(ctx context.Context, r *http.Request) (contract.Result, error) {
	if err := resourceEmptyRequest(r); err != nil {
		return contract.Result{}, err
	}
	id := r.PathValue("id")
	data, err := module.UpdateChain(ctx, s.options, func(state *chain.State) error {
		index := state.FindPolicy(id)
		if index < 0 {
			return &contract.Error{Code: "chain.policy_not_found", Message: "策略不存在", HTTPStatus: http.StatusNotFound}
		}
		state.Policies = slices.Delete(state.Policies, index, index+1)
		return nil
	})
	return contract.Success("chain.policy_deleted", "策略已删除", data), chainError(err)
}
