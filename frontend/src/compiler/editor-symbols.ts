import { cppLanguage } from "@codemirror/lang-cpp";
import type { SyntaxNode } from "@lezer/common";
import { standardSymbols } from "./standard-symbols";

export type SymbolKind = "variable" | "parameter" | "function" | "type" | "struct" | "enum" | "field" | "constant" | "macro";
export interface CodeSymbol {
  name: string;
  kind: SymbolKind;
  signature: string;
  from: number;
  to: number;
  scopeFrom: number;
  scopeTo: number;
  typeName?: string;
  owners?: string[];
  header?: string;
}

export interface Identifier {
  name: string;
  from: number;
  to: number;
  node: SyntaxNode;
}

const identifierNodes = new Set(["Identifier", "TypeIdentifier", "FieldIdentifier", "MacroName"]);
const typeNodes = new Set(["PrimitiveType", "SizedTypeSpecifier", "TypeIdentifier", "StructSpecifier", "UnionSpecifier", "EnumSpecifier"]);
const declaratorNodes = new Set(["InitDeclarator", "PointerDeclarator", "ArrayDeclarator", "FunctionDeclarator", "ParenthesizedDeclarator", "ReferenceDeclarator"]);

function children(node: SyntaxNode): SyntaxNode[] {
  const result: SyntaxNode[] = [];
  for (let child = node.firstChild; child; child = child.nextSibling) result.push(child);
  return result;
}

function ancestor(node: SyntaxNode | null, names: string[]): SyntaxNode | null {
  for (; node; node = node.parent) if (names.includes(node.name)) return node;
  return null;
}

function declaredName(node: SyntaxNode): SyntaxNode | null {
  if (identifierNodes.has(node.name)) return node;
  if (!declaratorNodes.has(node.name)) return null;
  for (const child of children(node)) {
    if (child.name === "ParameterList" || child.name === "ArgumentList") continue;
    const name = declaredName(child);
    if (name) return name;
  }
  return null;
}

function functionDeclarator(node: SyntaxNode): SyntaxNode | null {
  if (node.name === "FunctionDeclarator") return node;
  if (!declaratorNodes.has(node.name)) return null;
  for (const child of children(node)) {
    const found = functionDeclarator(child);
    if (found) return found;
  }
  return null;
}

function isFunction(node: SyntaxNode): boolean {
  const declaration = functionDeclarator(node);
  const name = declaration && declaredName(declaration);
  if (!declaration || !name) return false;
  // A pointer inside the function declarator is a function-pointer variable;
  // a pointer outside it is the function's return type.
  for (let part = name.parent; part && part.from >= declaration.from; part = part.parent) {
    if (part.name === "PointerDeclarator") return false;
    if (part.name === "FunctionDeclarator") return true;
  }
  return false;
}

function declarators(node: SyntaxNode): SyntaxNode[] {
  const result: SyntaxNode[] = [];
  let sawType = false;
  for (const child of children(node)) {
    if (declaratorNodes.has(child.name) || child.name === "Identifier" || child.name === "FieldIdentifier"
      || (child.name === "TypeIdentifier" && sawType)) result.push(child);
    else if (typeNodes.has(child.name)) sawType = true;
  }
  return result;
}

function scope(node: SyntaxNode, length: number): { scopeFrom: number; scopeTo: number } {
  const parent = ancestor(node.parent, ["CompoundStatement", "ForStatement"]);
  return { scopeFrom: parent?.from ?? 0, scopeTo: parent?.to ?? length };
}

export class CodeSymbols {
  readonly identifiers: Identifier[] = [];
  readonly definitions = new Map<string, CodeSymbol[]>();
  readonly includes = new Set<string>();
  private readonly byName = new Map<string, Identifier[]>();

  constructor(readonly code: string) {
    const tree = cppLanguage.parser.parse(code);
    tree.iterate({ enter: (cursor) => {
      const node = cursor.node;
      if (identifierNodes.has(node.name) || (["True", "False"].includes(node.name) && /^[A-Z_]+$/.test(this.text(node)))) {
        const identifier = { name: this.text(node), from: node.from, to: node.to, node };
        this.identifiers.push(identifier);
        const matches = this.byName.get(identifier.name) ?? [];
        matches.push(identifier);
        this.byName.set(identifier.name, matches);
      }
      if (["Declaration", "FieldDeclaration", "TypeDefinition", "FunctionDefinition", "ParameterDeclaration"].includes(node.name)) {
        this.addDeclaration(node);
      } else if (["StructSpecifier", "UnionSpecifier", "EnumSpecifier"].includes(node.name)) {
        this.addAggregate(node);
      } else if (node.name === "Enumerator") {
        const name = node.firstChild;
        if (name?.name === "Identifier") this.add({ name: this.text(name), kind: "constant", signature: this.text(node), from: name.from, to: name.to, ...scope(node, code.length) });
      } else if (node.name === "PreprocDirective") this.addPreprocessor(node);
    } });
  }

  private text(node: SyntaxNode): string { return this.code.slice(node.from, node.to); }

  private add(symbol: CodeSymbol) {
    const entries = this.definitions.get(symbol.name) ?? [];
    entries.push(symbol);
    this.definitions.set(symbol.name, entries);
  }

  private aggregateOwners(node: SyntaxNode): string[] {
    const tag = children(node).find((part) => part.name === "TypeIdentifier");
    const aliases = node.parent?.name === "TypeDefinition" ? declarators(node.parent).map(declaredName).filter((part): part is SyntaxNode => Boolean(part)) : [];
    return [...(tag ? [this.text(tag)] : []), ...aliases.map((part) => this.text(part))];
  }

  private addDeclaration(node: SyntaxNode) {
    const parts = declarators(node);
    if (!parts.length) return;
    const base = this.code.slice(node.from, parts[0].from).trim();
    const type = children(node).find((part) => typeNodes.has(part.name));
    const typeName = type?.name === "TypeIdentifier" ? this.text(type)
      : type && ["StructSpecifier", "UnionSpecifier", "EnumSpecifier"].includes(type.name)
        ? this.aggregateOwners(type)[0] : undefined;
    const aggregate = node.name === "FieldDeclaration" ? ancestor(node.parent, ["StructSpecifier", "UnionSpecifier"]) : null;

    for (const part of parts) {
      const name = declaredName(part);
      if (!name) continue;
      const kind: SymbolKind = node.name === "TypeDefinition" ? "type"
        : node.name === "FieldDeclaration" ? "field"
        : node.name === "ParameterDeclaration" ? "parameter"
        : isFunction(part) ? "function" : "variable";
      const enclosingFunction = kind === "parameter" ? ancestor(node.parent, ["FunctionDeclarator"]) : null;
      const parameterScope = enclosingFunction && ancestor(enclosingFunction, ["FunctionDefinition"]);
      // Only the outer function's parameters are visible in its body.
      const outerDeclarator = parameterScope && declarators(parameterScope)[0];
      const inFunctionBody = parameterScope && outerDeclarator && functionDeclarator(outerDeclarator)?.from === enclosingFunction?.from;
      const visibility = enclosingFunction ? {
        scopeFrom: inFunctionBody ? parameterScope.from : enclosingFunction.from,
        scopeTo: inFunctionBody ? parameterScope.to : enclosingFunction.to,
      } : scope(node, this.code.length);
      const signature = node.name === "TypeDefinition" && parts.length === 1 ? this.text(node).trim()
        : `${base} ${this.text(part).trim()}${kind === "parameter" ? "" : ";"}`;
      this.add({ name: this.text(name), kind, signature, from: name.from, to: name.to, ...visibility, typeName,
        ...(aggregate ? { owners: this.aggregateOwners(aggregate) } : {}),
      });
    }
  }

  private addAggregate(node: SyntaxNode) {
    if (!children(node).some((part) => ["FieldDeclarationList", "EnumeratorList"].includes(part.name))) return;
    const name = children(node).find((part) => part.name === "TypeIdentifier");
    if (!name) return;
    this.add({ name: this.text(name), kind: node.name === "EnumSpecifier" ? "enum" : "struct", signature: `${this.text(node)};`, from: name.from, to: name.to, ...scope(node, this.code.length) });
  }

  private addPreprocessor(node: SyntaxNode) {
    const end = this.code.indexOf("\n", node.from);
    const line = this.code.slice(node.from, end === -1 ? this.code.length : end);
    const include = /^#\s*include\s*[<"]([^>"]+)[>"]/.exec(line);
    if (include) this.includes.add(include[1]);
    const define = /^#\s*define\s+([a-zA-Z_]\w*)/.exec(line);
    if (!define) return;
    const from = node.from + define[0].lastIndexOf(define[1]);
    this.add({ name: define[1], kind: "macro", signature: line.trim(), from, to: from + define[1].length, scopeFrom: from, scopeTo: this.code.length });
  }

  identifierAt(position: number, side: -1 | 0 | 1 = 0): Identifier | undefined {
    let low = 0, high = this.identifiers.length;
    while (low < high) {
      const mid = (low + high) >>> 1;
      if (this.identifiers[mid].to < position || (side === 1 && this.identifiers[mid].to === position)) low = mid + 1;
      else high = mid;
    }
    const identifier = this.identifiers[low];
    return identifier && identifier.from <= position && identifier.to >= position
      && !(side === -1 && identifier.from === position) ? identifier : undefined;
  }

  occurrences(identifier: Identifier): readonly Identifier[] { return this.byName.get(identifier.name) ?? []; }

  private visibleDefinitions(name: string, position: number): CodeSymbol[] {
    return (this.definitions.get(name) ?? []).filter((symbol) => symbol.kind !== "field"
      && symbol.scopeFrom <= position && symbol.scopeTo >= position
      && (symbol.scopeFrom === 0 || symbol.kind === "function" || symbol.from <= position));
  }

  private resolveName(name: string, position: number, typeOnly = false): CodeSymbol | undefined {
    const candidates = this.visibleDefinitions(name, position).filter((symbol) =>
      !typeOnly || ["type", "struct", "enum"].includes(symbol.kind));
    return candidates.sort((a, b) => (a.scopeTo - a.scopeFrom) - (b.scopeTo - b.scopeFrom)
      || Number(b.kind === "type") - Number(a.kind === "type") || b.from - a.from)[0];
  }

  private canonicalType(name: string | undefined, position: number): string | undefined {
    const seen = new Set<string>();
    while (name && !seen.has(name)) {
      seen.add(name);
      const definition = this.resolveName(name, position, true);
      if (!definition?.typeName || definition.typeName === name) return name;
      name = definition.typeName;
    }
    return name;
  }

  private expressionType(node: SyntaxNode | null): string | undefined {
    if (!node) return undefined;
    if (identifierNodes.has(node.name)) return this.definition({ name: this.text(node), from: node.from, to: node.to, node })?.typeName;
    if (node.name === "FieldExpression") {
      const field = children(node).find((part) => part.name === "FieldIdentifier");
      if (field) return this.definition({ name: this.text(field), from: field.from, to: field.to, node: field })?.typeName;
    }
    if (["SubscriptExpression", "ParenthesizedExpression", "PointerExpression", "CallExpression"].includes(node.name)) {
      return this.expressionType(children(node).find((part) => /Identifier$|Expression$/.test(part.name)) ?? null);
    }
    return undefined;
  }

  definition(identifier: Identifier): CodeSymbol | undefined {
    const { name, node, from } = identifier;
    const declarations = this.definitions.get(name) ?? [];
    const own = declarations.find((symbol) => symbol.from === from);
    if (own) return own;
    if (node.name === "FieldIdentifier") {
      const receiver = node.parent?.name === "FieldExpression" ? node.parent.firstChild : null;
      const type = this.canonicalType(this.expressionType(receiver), from);
      const fields = declarations.filter((symbol) => symbol.kind === "field");
      return fields.find((symbol) => type && symbol.owners?.includes(type)) ?? (fields.length === 1 ? fields[0] : undefined);
    }
    const local = this.resolveName(name, from, node.name === "TypeIdentifier");
    if (local) return local;
    const standard = standardSymbols.get(name);
    if (!standard || !this.includes.has(standard.header)) return undefined;
    return { name, kind: "function", signature: standard.signature, header: standard.header, from: 0, to: 0, scopeFrom: 0, scopeTo: this.code.length };
  }
}
