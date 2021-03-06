import { ICartLine } from 'app/shared/model/cart-line.model';
import { IClient } from 'app/shared/model/client.model';

export interface ICart {
  id?: number;
  expired?: boolean;
  ordered?: boolean;
  cartLines?: ICartLine[];
  driver?: IClient;
}

export class Cart implements ICart {
  constructor(
    public id?: number,
    public expired?: boolean,
    public ordered?: boolean,
    public cartLines?: ICartLine[],
    public driver?: IClient
  ) {
    this.expired = this.expired || false;
    this.ordered = this.ordered || false;
  }
}
