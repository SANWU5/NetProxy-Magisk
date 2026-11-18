// Package region 根据节点名称识别节点所在的国家或地区，供节点列表展示与智能分组使用。
//
// 识别顺序：国旗 emoji → 中文国名与城市 → 英文国名与城市 → 国家/机场代码 → 「沪港」这类中转简称 → 回国节点。
// 同一类线索中取最长的匹配，避免「印度尼西亚」被识别为「印度」；中国大陆放在最后，
// 避免「上海-香港」这类中转线路被识别为入口城市所在地。
package region

import (
	"strings"
	"unicode/utf8"
)

// Region 描述一个国家或地区。Key 是小写的 ISO 3166-1 alpha-2 代码。
type Region struct {
	Key    string
	Name   string
	NameEn string
	Flag   string
}

// Label 返回带国旗的显示名称，english 为 true 时使用英文名称。
func (r Region) Label(english bool) string {
	name := r.Name
	if english || name == "" {
		name = r.NameEn
	}
	if r.Flag == "" {
		return name
	}
	return r.Flag + " " + name
}

type rule struct {
	key, name, nameEn string
	// names 是中文国名、别名与城市名，按子串匹配。
	names []string
	// words 是英文国名与城市名，前后不能紧邻字母。
	words []string
	// codes 是国家代码与机场代码，前后不能紧邻字母。
	codes []string
}

// 只收录在代理节点命名中常见、且不会与普通单词冲突的代码；有歧义的两字母代码（in、id、it、es、no 等）不收录。
var rules = []rule{
	{"hk", "香港", "Hong Kong", []string{"香港", "九龙", "新界"}, []string{"hong kong", "hongkong"}, []string{"hk", "hkg"}},
	{"mo", "澳门", "Macao", []string{"澳门", "澳門"}, []string{"macau", "macao"}, []string{"mo", "mfm"}},
	{"tw", "台湾", "Taiwan", []string{"台湾", "台灣", "台北", "新北", "台中", "台南", "高雄", "彰化", "桃园", "桃園"}, []string{"taiwan", "taipei", "kaohsiung"}, []string{"tw", "twn", "tpe"}},
	{"jp", "日本", "Japan", []string{"日本", "东京", "東京", "大阪", "名古屋", "埼玉", "横滨", "橫濱", "福冈", "京都", "川崎", "千叶"}, []string{"japan", "tokyo", "osaka", "nagoya", "saitama", "yokohama", "fukuoka"}, []string{"jp", "jpn", "nrt", "hnd", "kix"}},
	{"kr", "韩国", "South Korea", []string{"韩国", "韓國", "南韩", "首尔", "首爾", "春川", "釜山", "仁川"}, []string{"korea", "seoul", "chuncheon", "busan", "incheon"}, []string{"kr", "kor", "icn"}},
	{"sg", "新加坡", "Singapore", []string{"新加坡", "狮城", "獅城"}, []string{"singapore"}, []string{"sg", "sgp", "sin"}},
	{"us", "美国", "United States", []string{"美国", "美國", "洛杉矶", "洛杉磯", "圣何塞", "聖何塞", "硅谷", "矽谷", "西雅图", "西雅圖", "纽约", "紐約", "芝加哥", "达拉斯", "達拉斯", "迈阿密", "亚特兰大", "波特兰", "凤凰城", "弗吉尼亚", "圣克拉拉", "旧金山", "三藩市", "拉斯维加斯", "华盛顿", "费利蒙", "俄勒冈", "阿什本", "堪萨斯", "丹佛", "休斯顿", "夏威夷"}, []string{"united states", "america", "los angeles", "san jose", "silicon valley", "seattle", "new york", "chicago", "dallas", "miami", "atlanta", "portland", "phoenix", "virginia", "santa clara", "san francisco", "las vegas", "washington", "fremont", "oregon", "ashburn", "kansas", "denver", "houston", "hawaii"}, []string{"us", "usa", "lax", "sjc", "sea", "jfk", "ord", "dfw", "mia", "atl", "sfo", "iad"}},
	{"ca", "加拿大", "Canada", []string{"加拿大", "多伦多", "多倫多", "温哥华", "溫哥華", "蒙特利尔"}, []string{"canada", "toronto", "vancouver", "montreal"}, []string{"ca", "can", "yyz", "yvr"}},
	{"gb", "英国", "United Kingdom", []string{"英国", "英國", "伦敦", "倫敦", "曼彻斯特"}, []string{"united kingdom", "britain", "england", "london", "manchester"}, []string{"uk", "gb", "gbr", "lhr"}},
	{"de", "德国", "Germany", []string{"德国", "德國", "法兰克福", "法蘭克福", "柏林", "杜塞尔多夫", "慕尼黑"}, []string{"germany", "frankfurt", "berlin", "dusseldorf", "munich"}, []string{"de", "deu", "fra"}},
	{"fr", "法国", "France", []string{"法国", "法國", "巴黎", "马赛"}, []string{"france", "paris", "marseille"}, []string{"fr", "cdg"}},
	{"nl", "荷兰", "Netherlands", []string{"荷兰", "荷蘭", "阿姆斯特丹"}, []string{"netherlands", "holland", "amsterdam"}, []string{"nl", "nld", "ams"}},
	{"au", "澳大利亚", "Australia", []string{"澳大利亚", "澳大利亞", "澳洲", "悉尼", "墨尔本", "墨爾本", "布里斯班", "珀斯"}, []string{"australia", "sydney", "melbourne", "brisbane", "perth"}, []string{"au", "aus", "syd", "mel"}},
	{"nz", "新西兰", "New Zealand", []string{"新西兰", "新西蘭", "紐西蘭", "奥克兰"}, []string{"new zealand", "auckland"}, []string{"nz"}},
	{"in", "印度", "India", []string{"印度", "孟买", "孟買", "新德里", "班加罗尔"}, []string{"india", "mumbai", "delhi", "bangalore"}, []string{"ind", "bom"}},
	{"id", "印度尼西亚", "Indonesia", []string{"印度尼西亚", "印度尼西亞", "印尼", "雅加达", "雅加達"}, []string{"indonesia", "jakarta"}, []string{"idn", "cgk"}},
	{"my", "马来西亚", "Malaysia", []string{"马来西亚", "馬來西亞", "吉隆坡", "大马"}, []string{"malaysia", "kuala lumpur"}, []string{"mys", "kul"}},
	{"th", "泰国", "Thailand", []string{"泰国", "泰國", "曼谷"}, []string{"thailand", "bangkok"}, []string{"th", "tha", "bkk"}},
	{"vn", "越南", "Vietnam", []string{"越南", "胡志明", "河内", "河內"}, []string{"vietnam", "viet nam", "hanoi", "ho chi minh"}, []string{"vn", "vnm", "sgn"}},
	{"ph", "菲律宾", "Philippines", []string{"菲律宾", "菲律賓", "马尼拉", "馬尼拉"}, []string{"philippines", "manila"}, []string{"ph", "phl", "mnl"}},
	{"kh", "柬埔寨", "Cambodia", []string{"柬埔寨", "金边", "金邊"}, []string{"cambodia", "phnom penh"}, []string{"kh", "khm"}},
	{"mm", "缅甸", "Myanmar", []string{"缅甸", "緬甸", "仰光"}, []string{"myanmar", "yangon"}, nil},
	{"la", "老挝", "Laos", []string{"老挝", "老撾", "万象"}, []string{"laos", "vientiane"}, nil},
	{"bd", "孟加拉", "Bangladesh", []string{"孟加拉", "达卡"}, []string{"bangladesh", "dhaka"}, []string{"bd"}},
	{"pk", "巴基斯坦", "Pakistan", []string{"巴基斯坦", "卡拉奇"}, []string{"pakistan", "karachi"}, []string{"pk"}},
	{"np", "尼泊尔", "Nepal", []string{"尼泊尔", "尼泊爾"}, []string{"nepal"}, []string{"np"}},
	{"ae", "阿联酋", "United Arab Emirates", []string{"阿联酋", "阿聯酋", "迪拜", "杜拜", "阿布扎比"}, []string{"united arab emirates", "emirates", "dubai", "abu dhabi"}, []string{"uae", "ae", "dxb"}},
	{"sa", "沙特", "Saudi Arabia", []string{"沙特", "利雅得"}, []string{"saudi", "riyadh"}, nil},
	{"qa", "卡塔尔", "Qatar", []string{"卡塔尔", "卡達", "多哈"}, []string{"qatar", "doha"}, []string{"qa"}},
	{"il", "以色列", "Israel", []string{"以色列", "特拉维夫"}, []string{"israel", "tel aviv"}, []string{"il", "tlv"}},
	{"tr", "土耳其", "Türkiye", []string{"土耳其", "伊斯坦布尔", "伊斯坦堡"}, []string{"turkey", "turkiye", "türkiye", "istanbul"}, []string{"tr", "tur", "ist"}},
	{"ru", "俄罗斯", "Russia", []string{"俄罗斯", "俄羅斯", "莫斯科", "圣彼得堡", "新西伯利亚", "伯力", "哈巴罗夫斯克", "海参崴"}, []string{"russia", "moscow", "saint petersburg", "petersburg", "novosibirsk", "khabarovsk", "vladivostok"}, []string{"ru", "rus", "svo", "dme"}},
	{"ua", "乌克兰", "Ukraine", []string{"乌克兰", "烏克蘭", "基辅"}, []string{"ukraine", "kyiv", "kiev"}, []string{"ua", "ukr"}},
	{"kz", "哈萨克斯坦", "Kazakhstan", []string{"哈萨克斯坦", "哈薩克", "阿拉木图"}, []string{"kazakhstan", "almaty"}, []string{"kz"}},
	{"pl", "波兰", "Poland", []string{"波兰", "波蘭", "华沙"}, []string{"poland", "warsaw"}, []string{"pl", "waw"}},
	{"ch", "瑞士", "Switzerland", []string{"瑞士", "苏黎世", "蘇黎世"}, []string{"switzerland", "zurich"}, []string{"zrh"}},
	{"se", "瑞典", "Sweden", []string{"瑞典", "斯德哥尔摩"}, []string{"sweden", "stockholm"}, []string{"arn"}},
	{"no", "挪威", "Norway", []string{"挪威", "奥斯陆"}, []string{"norway", "oslo"}, nil},
	{"fi", "芬兰", "Finland", []string{"芬兰", "芬蘭", "赫尔辛基"}, []string{"finland", "helsinki"}, []string{"hel"}},
	{"dk", "丹麦", "Denmark", []string{"丹麦", "丹麥", "哥本哈根"}, []string{"denmark", "copenhagen"}, []string{"dk", "cph"}},
	{"ie", "爱尔兰", "Ireland", []string{"爱尔兰", "愛爾蘭", "都柏林"}, []string{"ireland", "dublin"}, []string{"dub"}},
	{"is", "冰岛", "Iceland", []string{"冰岛", "冰島"}, []string{"iceland", "reykjavik"}, nil},
	{"it", "意大利", "Italy", []string{"意大利", "義大利", "米兰", "米蘭", "罗马"}, []string{"italy", "milan", "rome"}, []string{"mxp", "fco"}},
	{"es", "西班牙", "Spain", []string{"西班牙", "马德里", "馬德里", "巴塞罗那"}, []string{"spain", "madrid", "barcelona"}, []string{"mad", "bcn"}},
	{"pt", "葡萄牙", "Portugal", []string{"葡萄牙", "里斯本"}, []string{"portugal", "lisbon"}, []string{"pt", "lis"}},
	{"at", "奥地利", "Austria", []string{"奥地利", "奧地利", "维也纳"}, []string{"austria", "vienna"}, []string{"vie"}},
	{"be", "比利时", "Belgium", []string{"比利时", "比利時", "布鲁塞尔"}, []string{"belgium", "brussels"}, []string{"bru"}},
	{"cz", "捷克", "Czechia", []string{"捷克", "布拉格"}, []string{"czech", "prague"}, []string{"cz", "prg"}},
	{"ro", "罗马尼亚", "Romania", []string{"罗马尼亚", "羅馬尼亞", "布加勒斯特"}, []string{"romania", "bucharest"}, []string{"ro", "otp"}},
	{"hu", "匈牙利", "Hungary", []string{"匈牙利", "布达佩斯"}, []string{"hungary", "budapest"}, []string{"hu", "bud"}},
	{"gr", "希腊", "Greece", []string{"希腊", "希臘", "雅典"}, []string{"greece", "athens"}, []string{"gr", "ath"}},
	{"bg", "保加利亚", "Bulgaria", []string{"保加利亚", "保加利亞", "索非亚"}, []string{"bulgaria", "sofia"}, []string{"bg"}},
	{"rs", "塞尔维亚", "Serbia", []string{"塞尔维亚", "塞爾維亞", "贝尔格莱德"}, []string{"serbia", "belgrade"}, []string{"rs"}},
	{"br", "巴西", "Brazil", []string{"巴西", "圣保罗", "聖保羅"}, []string{"brazil", "sao paulo", "são paulo"}, []string{"br", "bra", "gru"}},
	{"ar", "阿根廷", "Argentina", []string{"阿根廷", "布宜诺斯艾利斯"}, []string{"argentina", "buenos aires"}, []string{"arg"}},
	{"mx", "墨西哥", "Mexico", []string{"墨西哥"}, []string{"mexico", "méxico"}, []string{"mx", "mex"}},
	{"cl", "智利", "Chile", []string{"智利", "圣地亚哥"}, []string{"chile", "santiago"}, []string{"cl", "scl"}},
	{"co", "哥伦比亚", "Colombia", []string{"哥伦比亚", "哥倫比亞", "波哥大"}, []string{"colombia", "bogota"}, []string{"bog"}},
	{"pe", "秘鲁", "Peru", []string{"秘鲁", "秘魯", "利马"}, []string{"peru", "lima"}, nil},
	{"za", "南非", "South Africa", []string{"南非", "约翰内斯堡", "開普敦", "开普敦"}, []string{"south africa", "johannesburg", "cape town"}, []string{"za", "jnb"}},
	{"eg", "埃及", "Egypt", []string{"埃及", "开罗", "開羅"}, []string{"egypt", "cairo"}, []string{"cai"}},
	{"ng", "尼日利亚", "Nigeria", []string{"尼日利亚", "尼日利亞", "拉各斯"}, []string{"nigeria", "lagos"}, []string{"ng"}},
	{"ke", "肯尼亚", "Kenya", []string{"肯尼亚", "肯亞", "内罗毕"}, []string{"kenya", "nairobi"}, []string{"ke"}},
	{"cn", "中国", "China", []string{"中国", "中國", "回国", "回國", "大陆", "大陸", "国内", "國內"}, []string{"china", "mainland"}, []string{"cn", "chn"}},
}

// transit 是「沪港」「深日」这类中转简称中的出口地区。
var transit = map[rune]string{
	'港': "hk", '台': "tw", '日': "jp", '韩': "kr", '韓': "kr", '美': "us", '新': "sg",
	'英': "gb", '德': "de", '法': "fr", '俄': "ru", '加': "ca",
}

// transitEntries 是中转线路常见的入口城市简称。
const transitEntries = "沪滬深广廣京杭苏蘇宁寧蓉渝"

// informational 中的词只出现在流量、到期、官网等提示性节点里，这类节点不参与分组。
var informational = []string{
	"剩余", "剩餘", "已用", "总流量", "總流量", "到期", "过期", "過期", "重置", "官网", "官網", "网址", "網址",
	"套餐", "续费", "續費", "客服", "公告", "频道", "頻道", "失联", "失聯", "订阅", "訂閱", "距离下次", "有效期",
	"使用文档", "教程", "加群", "expire", "traffic", "remaining", "reset", "website", "subscription",
}

var byKey = func() map[string]*rule {
	index := make(map[string]*rule, len(rules))
	for i := range rules {
		index[rules[i].key] = &rules[i]
	}
	return index
}()

// Lookup 返回指定代码的地区；表中没有的代码只返回代码本身和国旗。
func Lookup(key string) Region {
	key = strings.ToLower(key)
	if r, ok := byKey[key]; ok {
		return Region{Key: r.key, Name: r.name, NameEn: r.nameEn, Flag: Flag(r.key)}
	}
	return Region{Key: key, NameEn: strings.ToUpper(key), Flag: Flag(key)}
}

// Flag 把两个字母的地区代码转换为国旗 emoji。
func Flag(key string) string {
	if len(key) != 2 || key[0] < 'a' || key[0] > 'z' || key[1] < 'a' || key[1] > 'z' {
		return ""
	}
	return string([]rune{rune(0x1F1E6 + int(key[0]-'a')), rune(0x1F1E6 + int(key[1]-'a'))})
}

// Informational 判断节点是否只是流量、到期、官网等提示信息。
func Informational(tag string) bool {
	lower := strings.ToLower(tag)
	for _, token := range informational {
		if strings.Contains(lower, token) {
			return true
		}
	}
	return false
}

// Detect 识别节点名称对应的地区；无法识别或是提示性节点时返回 false。
func Detect(tag string) (Region, bool) {
	if Informational(tag) {
		return Region{}, false
	}
	if key := flagKey(tag); key != "" && key != "un" && key != "eu" {
		return Lookup(key), true
	}
	lower := strings.ToLower(tag)
	best, bestScore := (*rule)(nil), 0
	for i := range rules {
		r := &rules[i]
		if r.key == "cn" {
			continue
		}
		if score := r.score(lower); score > bestScore {
			best, bestScore = r, score
		}
	}
	if best != nil {
		return Lookup(best.key), true
	}
	if key := transitKey(tag); key != "" {
		return Lookup(key), true
	}
	if byKey["cn"].score(lower) > 0 {
		return Lookup("cn"), true
	}
	return Region{}, false
}

// score 返回规则在节点名称中的最佳匹配得分：中文线索优先于英文名称，英文名称优先于代码，同类中越长越优先。
func (r *rule) score(lower string) int {
	best := 0
	for _, token := range r.names {
		if strings.Contains(lower, token) {
			best = max(best, 3000+utf8.RuneCountInString(token))
		}
	}
	for _, token := range r.words {
		if containsBounded(lower, token, isLetter) {
			best = max(best, 2000+len(token))
		}
	}
	// 代码后面常直接跟编号（HK01、JP02），因此只要求前后不是字母。
	for _, token := range r.codes {
		if containsBounded(lower, token, isLetter) {
			best = max(best, 1000+len(token))
		}
	}
	return best
}

// flagKey 返回名称中第一个国旗 emoji 对应的地区代码。
func flagKey(tag string) string {
	var first rune
	for _, current := range tag {
		if current < 0x1F1E6 || current > 0x1F1FF {
			first = 0
			continue
		}
		if first == 0 {
			first = current
			continue
		}
		return string([]byte{byte('a' + first - 0x1F1E6), byte('a' + current - 0x1F1E6)})
	}
	return ""
}

func transitKey(tag string) string {
	previous := rune(0)
	for _, current := range tag {
		if strings.ContainsRune(transitEntries, previous) {
			if key, ok := transit[current]; ok {
				return key
			}
		}
		previous = current
	}
	return ""
}

// containsBounded 判断 token 在 text 中出现且前后都不是 boundary 认定的字符。
func containsBounded(text, token string, boundary func(byte) bool) bool {
	for start := 0; start <= len(text)-len(token); {
		index := strings.Index(text[start:], token)
		if index < 0 {
			return false
		}
		begin, end := start+index, start+index+len(token)
		if (begin == 0 || !boundary(text[begin-1])) && (end == len(text) || !boundary(text[end])) {
			return true
		}
		start = begin + 1
	}
	return false
}

func isLetter(value byte) bool { return value >= 'a' && value <= 'z' }
