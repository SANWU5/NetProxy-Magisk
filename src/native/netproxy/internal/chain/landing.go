package chain

import (
	"context"
	"errors"
	"fmt"
	"net"
	"net/url"
	"strconv"
	"strings"

	"github.com/Fanju6/NetProxy-Magisk/src/native/netproxy/internal/convert"
	"github.com/Fanju6/NetProxy-Magisk/src/native/netproxy/internal/provider"
	C "github.com/sagernet/sing-box/constant"
	"github.com/sagernet/sing-box/option"
)

// 不带协议头的「主机:端口:用户名:密码」行使用的协议。
const (
	DefaultTypeSOCKS = "socks"
	DefaultTypeHTTP  = "http"
)

// ImportResult 描述一次后置代理导入。
type ImportResult struct {
	Added       []string              `json:"added"`
	Skipped     []string              `json:"skipped"`
	Diagnostics []provider.Diagnostic `json:"diagnostics,omitempty"`
}

// ParseLandings 解析多行后置代理输入。支持：
//   - 主机:端口、主机:端口:用户名:密码（密码可以包含冒号，IPv6 主机写成 [地址]）
//   - 用户名:密码@主机:端口
//   - socks5://、socks://、socks5h://、http://、https:// 等带协议头的链接，以及其他已支持的分享链接
//
// 不带协议头的行使用 defaultType。单行失败只记录诊断，不影响其他行。
func ParseLandings(ctx context.Context, content, defaultType string) (provider.Document, []provider.Diagnostic, error) {
	if defaultType == "" {
		defaultType = DefaultTypeSOCKS
	}
	if defaultType != DefaultTypeSOCKS && defaultType != DefaultTypeHTTP {
		return provider.Document{}, nil, fmt.Errorf("默认协议只能是 socks 或 http，当前为 %q", defaultType)
	}
	var document provider.Document
	var diagnostics []provider.Diagnostic
	lines := strings.Split(strings.ReplaceAll(content, "\r\n", "\n"), "\n")
	for index, raw := range lines {
		line := strings.TrimSpace(raw)
		if line == "" || strings.HasPrefix(line, "#") || strings.HasPrefix(line, "//") {
			continue
		}
		outbound, err := parseLandingLine(ctx, line, defaultType)
		if err != nil {
			diagnostics = append(diagnostics, provider.Diagnostic{Index: index + 1, Source: lineLabel(line), Code: "landing.invalid", Message: err.Error()})
			continue
		}
		document.Outbounds = append(document.Outbounds, outbound)
	}
	if len(document.Outbounds) == 0 {
		if len(diagnostics) == 0 {
			return provider.Document{}, nil, errors.New("没有找到可导入的后置代理")
		}
		return provider.Document{}, diagnostics, fmt.Errorf("没有可导入的后置代理：第 %d 行 %s", diagnostics[0].Index, diagnostics[0].Message)
	}
	return document, diagnostics, nil
}

func parseLandingLine(ctx context.Context, line, defaultType string) (option.Outbound, error) {
	link, name, err := normalizeLandingLine(line, defaultType)
	if err != nil {
		return option.Outbound{}, err
	}
	result, err := convert.Link(ctx, link, false)
	if err != nil {
		if len(result.Diagnostics) > 0 {
			return option.Outbound{}, errors.New(result.Diagnostics[0].Message)
		}
		return option.Outbound{}, err
	}
	if len(result.Document.Outbounds) != 1 {
		return option.Outbound{}, errors.New("链接必须恰好包含一个节点")
	}
	outbound := result.Document.Outbounds[0]
	if _, ok := outbound.Options.(option.DialerOptionsWrapper); !ok {
		return option.Outbound{}, fmt.Errorf("协议 %s 不支持经由前置节点连接", outbound.Type)
	}
	if name == "" {
		name = defaultLandingName(outbound)
	}
	outbound.Tag = name
	if err := ValidateName(outbound.Tag); err != nil {
		outbound.Tag = defaultLandingName(outbound)
	}
	return outbound, nil
}

// normalizeLandingLine 把一行输入转换为 convert 能解析的链接，并返回用户写在 # 后面的名称。
func normalizeLandingLine(line, defaultType string) (string, string, error) {
	if scheme, rest, found := strings.Cut(line, "://"); found {
		name := ""
		if before, fragment, ok := strings.Cut(rest, "#"); ok {
			rest = before
			if decoded, err := url.PathUnescape(fragment); err == nil {
				name = strings.TrimSpace(decoded)
			} else {
				name = strings.TrimSpace(fragment)
			}
		}
		switch strings.ToLower(scheme) {
		case "socks", "socks5", "socks5h":
			scheme = "socks5"
		case "socks4", "socks4a":
			return "", "", errors.New("只支持 SOCKS5，不支持 SOCKS4")
		case "http", "https":
			scheme = strings.ToLower(scheme)
		}
		return scheme + "://" + rest, name, nil
	}
	scheme := "socks5"
	if defaultType == DefaultTypeHTTP {
		scheme = "http"
	}
	var user *url.Userinfo
	hostPort := line
	if credentials, address, found := cutLast(line, "@"); found {
		username, password, _ := strings.Cut(credentials, ":")
		user = url.UserPassword(username, password)
		hostPort = address
	} else {
		host, port, rest, err := splitHostPortPrefix(line)
		if err != nil {
			return "", "", err
		}
		hostPort = net.JoinHostPort(host, port)
		if rest != "" {
			username, password, _ := strings.Cut(rest, ":")
			user = url.UserPassword(username, password)
		}
	}
	host, port, err := net.SplitHostPort(hostPort)
	if err != nil {
		return "", "", fmt.Errorf("无法识别主机和端口: %w", err)
	}
	if err := validatePort(port); err != nil {
		return "", "", err
	}
	if strings.TrimSpace(host) == "" {
		return "", "", errors.New("缺少主机地址")
	}
	link := url.URL{Scheme: scheme, User: user, Host: net.JoinHostPort(host, port)}
	return link.String(), "", nil
}

// splitHostPortPrefix 拆出「主机:端口」以及之后剩余的「用户名:密码」。
func splitHostPortPrefix(line string) (host, port, rest string, err error) {
	if strings.HasPrefix(line, "[") {
		end := strings.Index(line, "]")
		if end < 0 {
			return "", "", "", errors.New("IPv6 地址缺少右方括号")
		}
		host = line[1:end]
		remainder := strings.TrimPrefix(line[end+1:], ":")
		port, rest, _ = strings.Cut(remainder, ":")
	} else {
		parts := strings.SplitN(line, ":", 3)
		if len(parts) < 2 {
			return "", "", "", errors.New("格式应为 主机:端口 或 主机:端口:用户名:密码")
		}
		host, port = parts[0], parts[1]
		if len(parts) == 3 {
			rest = parts[2]
		}
	}
	if err := validatePort(port); err != nil {
		return "", "", "", err
	}
	if rest != "" && !strings.Contains(rest, ":") {
		return "", "", "", errors.New("用户名和密码之间需要用冒号分隔")
	}
	return host, port, rest, nil
}

func cutLast(value, separator string) (string, string, bool) {
	index := strings.LastIndex(value, separator)
	if index < 0 {
		return "", value, false
	}
	return value[:index], value[index+len(separator):], true
}

func validatePort(value string) error {
	port, err := strconv.ParseUint(value, 10, 16)
	if err != nil || port == 0 {
		return fmt.Errorf("端口 %q 无效", value)
	}
	return nil
}

func defaultLandingName(outbound option.Outbound) string {
	if server, ok := outbound.Options.(option.ServerOptionsWrapper); ok {
		options := server.TakeServerOptions()
		if options.Server != "" {
			return net.JoinHostPort(options.Server, strconv.Itoa(int(options.ServerPort)))
		}
	}
	return outbound.Type
}

func lineLabel(line string) string {
	if scheme, _, found := strings.Cut(line, "://"); found {
		return strings.ToLower(scheme) + "://..."
	}
	return "第一段: " + strings.SplitN(line, ":", 2)[0]
}

// AppendLandings 把新解析的后置代理加入列表，跳过已存在的相同代理并为重名追加序号。
func (state *State) AppendLandings(document provider.Document) ImportResult {
	result := ImportResult{Added: []string{}, Skipped: []string{}}
	existing := make(map[string]string, len(state.Landings.Outbounds))
	for _, outbound := range state.Landings.Outbounds {
		existing[landingIdentity(outbound)] = outbound.Tag
	}
	fresh := provider.Document{}
	for _, outbound := range document.Outbounds {
		identity := landingIdentity(outbound)
		if tag, exists := existing[identity]; exists && identity != "" {
			result.Skipped = append(result.Skipped, tag)
			continue
		}
		existing[identity] = outbound.Tag
		fresh.Outbounds = append(fresh.Outbounds, outbound)
	}
	start := len(state.Landings.Outbounds)
	provider.Append(&state.Landings, fresh)
	for _, outbound := range state.Landings.Outbounds[start:] {
		result.Added = append(result.Added, outbound.Tag)
	}
	return result
}

// landingIdentity 用协议、地址与认证信息识别重复导入；其他协议不去重。
func landingIdentity(outbound option.Outbound) string {
	switch options := outbound.Options.(type) {
	case *option.SOCKSOutboundOptions:
		return strings.Join([]string{C.TypeSOCKS, options.Server, strconv.Itoa(int(options.ServerPort)), options.Username, options.Password}, "\x00")
	case *option.HTTPOutboundOptions:
		return strings.Join([]string{C.TypeHTTP, options.Server, strconv.Itoa(int(options.ServerPort)), options.Username, options.Password}, "\x00")
	}
	return ""
}

// RemoveLanding 删除后置代理；仍被手动模式或策略引用时拒绝删除。
func (state *State) RemoveLanding(tag string) error {
	if state.FindLanding(tag) < 0 {
		return fmt.Errorf("后置代理 %q 不存在", tag)
	}
	if state.Manual == tag {
		if UsesManual(state.Mode) {
			return fmt.Errorf("后置代理 %q 正在被手动模式使用，请先切换手动后置或模式", tag)
		}
		state.Manual = ""
	}
	for _, policy := range state.Policies {
		if policy.Landing == tag {
			return fmt.Errorf("后置代理 %q 正在被策略 %q 使用，请先修改该策略", tag, policy.Name)
		}
	}
	provider.Remove(&state.Landings, tag)
	if len(state.Landings.Outbounds) == 0 && state.Mode != ModeOff {
		state.Mode = ModeOff
	}
	return nil
}

// RenameLanding 修改后置代理名称，并同步手动模式与策略中的引用。
func (state *State) RenameLanding(tag, name string) error {
	index := state.FindLanding(tag)
	if index < 0 {
		return fmt.Errorf("后置代理 %q 不存在", tag)
	}
	if err := ValidateName(name); err != nil {
		return err
	}
	if name == tag {
		return nil
	}
	if state.FindLanding(name) >= 0 {
		return fmt.Errorf("已存在名为 %q 的后置代理", name)
	}
	state.Landings.Outbounds[index].Tag = name
	if state.Manual == tag {
		state.Manual = name
	}
	for policyIndex := range state.Policies {
		if state.Policies[policyIndex].Landing == tag {
			state.Policies[policyIndex].Landing = name
		}
	}
	return nil
}

// LandingSummary 是客户端可见的后置代理摘要，不包含密码。
type LandingSummary struct {
	Tag         string `json:"tag"`
	Type        string `json:"type"`
	Server      string `json:"server"`
	Port        uint16 `json:"port"`
	Username    string `json:"username,omitempty"`
	HasPassword bool   `json:"has_password"`
	TLS         bool   `json:"tls"`
}

// Summaries 返回后置代理摘要，顺序与列表一致。
func (state State) Summaries() []LandingSummary {
	result := make([]LandingSummary, 0, len(state.Landings.Outbounds))
	for _, outbound := range state.Landings.Outbounds {
		summary := LandingSummary{Tag: outbound.Tag, Type: outbound.Type}
		if server, ok := outbound.Options.(option.ServerOptionsWrapper); ok {
			options := server.TakeServerOptions()
			summary.Server, summary.Port = options.Server, options.ServerPort
		}
		switch options := outbound.Options.(type) {
		case *option.SOCKSOutboundOptions:
			summary.Username, summary.HasPassword = options.Username, options.Password != ""
		case *option.HTTPOutboundOptions:
			summary.Username, summary.HasPassword = options.Username, options.Password != ""
			summary.TLS = options.TLS != nil && options.TLS.Enabled
		}
		if wrapper, ok := outbound.Options.(option.OutboundTLSOptionsWrapper); ok {
			if options := wrapper.TakeOutboundTLSOptions(); options != nil && options.Enabled {
				summary.TLS = true
			}
		}
		result = append(result, summary)
	}
	return result
}
