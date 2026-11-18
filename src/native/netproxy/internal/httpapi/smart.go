package httpapi

import (
	"context"
	"errors"
	"net/http"

	"github.com/Fanju6/NetProxy-Magisk/src/native/netproxy/internal/contract"
	"github.com/Fanju6/NetProxy-Magisk/src/native/netproxy/internal/module"
	"github.com/Fanju6/NetProxy-Magisk/src/native/netproxy/internal/smartgroup"
)

// smartError 把分组不存在转换为 404、输入校验错误转换为 422，保留其他结构化错误。
func smartError(err error) error {
	if err == nil {
		return nil
	}
	if errors.Is(err, module.ErrSmartGroupNotFound) {
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

type smartGroupInput struct {
	Name    string               `json:"name"`
	Members []smartgroup.NodeRef `json:"members"`
}

func (s *Server) readSmartGroups(ctx context.Context, r *http.Request) (contract.Result, error) {
	if _, err := resourceQuery(r); err != nil {
		return contract.Result{}, err
	}
	data, err := module.ReadSmartGroups(ctx, s.options)
	return contract.Success("smart.list", "智能分组", data), err
}

func (s *Server) generateSmartGroups(ctx context.Context, r *http.Request) (contract.Result, error) {
	var input struct {
		English bool `json:"english"`
	}
	if err := resourceDecode(r, &input); err != nil {
		return contract.Result{}, err
	}
	view, summary, err := module.GenerateSmartGroups(ctx, s.options, input.English)
	return contract.Success("smart.generated", "智能分组已完成", map[string]any{"smart": view, "summary": summary}), smartError(err)
}

func (s *Server) createSmartGroup(ctx context.Context, r *http.Request) (contract.Result, error) {
	var input smartGroupInput
	if err := resourceDecode(r, &input); err != nil {
		return contract.Result{}, err
	}
	view, id, err := module.SaveSmartGroup(ctx, s.options, "", input.Name, input.Members)
	return contract.Success("smart.created", "分组已创建", map[string]any{"smart": view, "id": id}), smartError(err)
}

func (s *Server) saveSmartGroup(ctx context.Context, r *http.Request) (contract.Result, error) {
	var input smartGroupInput
	if err := resourceDecode(r, &input); err != nil {
		return contract.Result{}, err
	}
	view, id, err := module.SaveSmartGroup(ctx, s.options, r.PathValue("id"), input.Name, input.Members)
	return contract.Success("smart.saved", "分组已保存", map[string]any{"smart": view, "id": id}), smartError(err)
}

func (s *Server) deleteSmartGroup(ctx context.Context, r *http.Request) (contract.Result, error) {
	if err := resourceEmptyRequest(r); err != nil {
		return contract.Result{}, err
	}
	view, err := module.DeleteSmartGroup(ctx, s.options, r.PathValue("id"))
	return contract.Success("smart.deleted", "分组已删除", view), smartError(err)
}

func (s *Server) testSmartGroups(ctx context.Context, r *http.Request) (contract.Result, error) {
	var input struct {
		ID string `json:"id"`
	}
	if err := resourceDecode(r, &input); err != nil {
		return contract.Result{}, err
	}
	view, err := module.TestSmartGroups(ctx, s.options, input.ID)
	return contract.Success("smart.delay", "分组测速完成", view), smartError(err)
}
